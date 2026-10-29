package dev.opecko.sonocraft.api.rpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.api.ConnectionClosedException;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.platform.Transport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single JSON-RPC connection to Sonolith (SPEC §4).
 *
 * <ul>
 *   <li>The reader thread only parses and hands off, so it never stalls (Sonolith drops a client that doesn't read for 10 s).
 *   <li>Writes run on their own thread from a queue, so {@link #call} never blocks.
 *   <li>At most {@value #MAX_IN_FLIGHT} requests in flight; the rest wait in the queue.
 * </ul>
 *
 * <p>Futures from {@link #call} and the {@link Listener} run on the reader thread. Callers must hand off quickly.
 */
public final class RpcConnection implements AutoCloseable {
    public static final int MAX_MESSAGE = 1024 * 1024;
    public static final int MAX_IN_FLIGHT = 16;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(35);

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Sonocraft-RpcTimer");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger CONNECTION_IDS = new AtomicInteger();
    private static final byte[] POISON = new byte[0];

    /** Notifications and connection close. Called on the reader thread; must not block. */
    public interface Listener {
        void onNotification(String method, JsonObject params);

        default void onClosed(Throwable cause) {
        }
    }

    private record Pending(String method, CompletableFuture<JsonElement> future, byte[] line,
                           ScheduledFuture<?> timeout) {
    }

    private final Transport transport;
    private final Listener listener;
    private final Duration timeout;
    private final int connectionId = CONNECTION_IDS.incrementAndGet();
    private final Object lock = new Object();
    private final Map<Long, Pending> inFlight = new HashMap<>();
    private final ArrayDeque<Long> waiting = new ArrayDeque<>();
    private final Map<Long, Pending> waitingCalls = new HashMap<>();
    private final LinkedBlockingQueue<byte[]> writeQueue = new LinkedBlockingQueue<>();
    private final CompletableFuture<Throwable> closed = new CompletableFuture<>();
    private long nextId = 1;
    private boolean isClosed;

    public RpcConnection(Transport transport, Listener listener) {
        this(transport, listener, DEFAULT_TIMEOUT);
    }

    public RpcConnection(Transport transport, Listener listener, Duration timeout) {
        this.transport = transport;
        this.listener = listener;
        this.timeout = timeout;
    }

    public RpcConnection start() {
        startThread("Sonocraft-IO-Reader-" + connectionId, this::readLoop);
        startThread("Sonocraft-IO-Writer-" + connectionId, this::writeLoop);
        return this;
    }

    private static void startThread(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends a request. The future completes with the result, {@link SonolithException}, {@link ConnectionClosedException}
     * or {@link TimeoutException}.
     */
    public CompletableFuture<JsonElement> call(String method, JsonObject params) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        synchronized (lock) {
            if (isClosed) {
                future.completeExceptionally(new ConnectionClosedException(method + ": connection closed"));
                return future;
            }
            long id = nextId++;
            JsonObject msg = new JsonObject();
            msg.addProperty("jsonrpc", "2.0");
            msg.addProperty("id", id);
            msg.addProperty("method", method);
            if (params != null && !params.isEmpty()) {
                msg.add("params", params);
            }
            byte[] line = (msg + "\n").getBytes(StandardCharsets.UTF_8);
            if (line.length - 1 > MAX_MESSAGE) {
                future.completeExceptionally(new IllegalArgumentException(method + ": request over 1 MiB"));
                return future;
            }
            ScheduledFuture<?> t = TIMER.schedule(() -> timedOut(id), timeout.toMillis(), TimeUnit.MILLISECONDS);
            Pending p = new Pending(method, future, line, t);
            if (inFlight.size() < MAX_IN_FLIGHT) {
                inFlight.put(id, p);
                writeQueue.add(line);
            } else {
                waitingCalls.put(id, p);
                waiting.add(id);
            }
        }
        return future;
    }

    /** Completes (with the cause or null) once the connection closes. */
    public CompletableFuture<Throwable> closed() {
        return closed;
    }

    public boolean isClosed() {
        synchronized (lock) {
            return isClosed;
        }
    }

    @Override
    public void close() {
        shutdown(null);
    }

    private void timedOut(long id) {
        Pending p;
        synchronized (lock) {
            p = inFlight.remove(id);
            if (p != null) {
                promoteWaiting();
            } else {
                p = waitingCalls.remove(id);
                waiting.remove(id);
            }
        }
        if (p != null) {
            p.future().completeExceptionally(new TimeoutException(p.method() + ": no answer in " + timeout));
        }
    }

    /** Under the lock: a freed in-flight slot goes to the oldest waiting request. */
    private void promoteWaiting() {
        while (inFlight.size() < MAX_IN_FLIGHT && !waiting.isEmpty()) {
            long id = waiting.poll();
            Pending p = waitingCalls.remove(id);
            if (p != null) {
                inFlight.put(id, p);
                writeQueue.add(p.line());
            }
        }
    }

    private void readLoop() {
        Throwable cause = null;
        try {
            LineReader reader = new LineReader(transport, MAX_MESSAGE);
            byte[] line;
            while ((line = reader.next()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            cause = e;
        } catch (RuntimeException e) {
            Sonocraft.LOGGER.error("Sonolith reader failed", e);
            cause = e;
        }
        shutdown(cause);
    }

    private void writeLoop() {
        try {
            while (true) {
                byte[] line = writeQueue.take();
                if (line == POISON) {
                    return;
                }
                transport.write(line, 0, line.length);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            shutdown(e);
        }
    }

    private void handleLine(byte[] bytes) {
        JsonObject msg;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            JsonElement e = JsonParser.parseString(text);
            if (!e.isJsonObject()) {
                Sonocraft.LOGGER.warn("Ignoring a non-object message from Sonolith");
                return;
            }
            msg = e.getAsJsonObject();
        } catch (CharacterCodingException | JsonParseException e) {
            Sonocraft.LOGGER.warn("Ignoring an unreadable message from Sonolith: {}", e.getMessage());
            return;
        }
        JsonElement id = msg.get("id");
        if (msg.has("result") || msg.has("error")) {
            handleResponse(id, msg);
        } else if (msg.get("method") instanceof JsonPrimitive m && m.isString() && (id == null || id.isJsonNull())) {
            JsonElement params = msg.get("params");
            String method = m.getAsString();
            try {
                listener.onNotification(method, params != null && params.isJsonObject()
                        ? params.getAsJsonObject() : new JsonObject());
            } catch (RuntimeException e) {
                Sonocraft.LOGGER.error("Sonolith event handler failed for {}", method, e);
            }
        }
    }

    private void handleResponse(JsonElement id, JsonObject msg) {
        if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isNumber()) {
            // ParseError / messageTooLarge with null id: client error, nothing is waiting
            Sonocraft.LOGGER.warn("Sonolith reported an error without a request id: {}", msg.get("error"));
            return;
        }
        Pending p;
        synchronized (lock) {
            p = inFlight.remove(id.getAsLong());
            if (p != null) {
                promoteWaiting();
            }
        }
        if (p == null) {
            return; // already timed out
        }
        p.timeout().cancel(false);
        JsonElement error = msg.get("error");
        if (error != null && error.isJsonObject()) {
            p.future().completeExceptionally(SonolithException.fromError(error.getAsJsonObject(), p.method()));
        } else {
            p.future().complete(msg.get("result"));
        }
    }

    private void shutdown(Throwable cause) {
        List<Pending> failed = new ArrayList<>();
        synchronized (lock) {
            if (isClosed) {
                return;
            }
            isClosed = true;
            failed.addAll(inFlight.values());
            failed.addAll(waitingCalls.values());
            inFlight.clear();
            waitingCalls.clear();
            waiting.clear();
            writeQueue.clear();
            writeQueue.add(POISON);
        }
        transport.close();
        for (Pending p : failed) {
            p.timeout().cancel(false);
            p.future().completeExceptionally(new ConnectionClosedException(p.method() + ": connection closed", cause));
        }
        try {
            listener.onClosed(cause);
        } catch (RuntimeException e) {
            Sonocraft.LOGGER.error("Sonolith close handler failed", e);
        }
        closed.complete(cause);
    }
}
