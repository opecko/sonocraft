package dev.opecko.sonocraft.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.NotAvailableException;
import dev.opecko.sonocraft.api.rpc.LineReader;
import dev.opecko.sonocraft.platform.Connector;
import dev.opecko.sonocraft.platform.UnixTransport;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sonolith stand-in for tests (based on {@code python-tui/tests/fake_server.py}): line-delimited JSON-RPC over a
 * Unix socket, registration with a scripted answer, session, events. Records every call.
 */
public final class FakeSonolith implements AutoCloseable {
    public static final String TOKEN = "snl_test_token";

    public record Call(String method, JsonObject params) {
    }

    /** User's answer on the consent screen: approved, denied, blocked, expired, or null (no answer). */
    public volatile String consent = "approved";
    /** Granted scopes; null = all the app asks for. */
    public volatile List<String> granted;
    public volatile long retryMs = 60_000;
    /** Error returned by session.hello (e.g. "Unauthorized"), or null. */
    public volatile String helloError;
    /** How many times session.hello answers RegistrationPending (identityChanged). */
    public final AtomicInteger identityChanges = new AtomicInteger();
    /** User's answer to the identity change. */
    public volatile String identityConsent = "approved";

    public final List<Call> calls = new CopyOnWriteArrayList<>();
    public final AtomicInteger connections = new AtomicInteger();
    private final List<UnixTransport> clients = new CopyOnWriteArrayList<>();
    private final Path dir;
    private final Path socket;
    private volatile ServerSocketChannel server;

    public FakeSonolith() throws IOException {
        dir = Files.createTempDirectory("snc-fake");
        socket = dir.resolve("api.sock");
    }

    public Connector connector() {
        return () -> {
            if (server == null) {
                throw new NotAvailableException("fake Sonolith not running");
            }
            return UnixTransport.open(socket);
        };
    }

    public synchronized void start() throws IOException {
        ServerSocketChannel ss = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ss.bind(UnixDomainSocketAddress.of(socket));
        server = ss;
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    UnixTransport c = UnixTransport.wrap(ss.accept());
                    connections.incrementAndGet();
                    clients.add(c);
                    Thread h = new Thread(() -> serve(c), "fake-sonolith-conn");
                    h.setDaemon(true);
                    h.start();
                }
            } catch (IOException e) {
                // server closed
            }
        }, "fake-sonolith-accept");
        t.setDaemon(true);
        t.start();
    }

    /** Sonolith shuts down: closes connections and the socket. */
    public synchronized void stop() throws IOException {
        if (server != null) {
            server.close();
            server = null;
        }
        dropConnections();
        Files.deleteIfExists(socket);
    }

    public void dropConnections() {
        for (UnixTransport c : clients) {
            c.close();
        }
        clients.clear();
    }

    @Override
    public void close() throws IOException {
        stop();
        Files.deleteIfExists(dir);
    }

    public List<JsonObject> called(String method) {
        List<JsonObject> out = new ArrayList<>();
        for (Call c : calls) {
            if (c.method().equals(method)) {
                out.add(c.params());
            }
        }
        return out;
    }

    public List<String> methods() {
        return calls.stream().map(Call::method).toList();
    }

    /** Sends a notification to all connected clients. */
    public void push(String method, JsonObject params) {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.addProperty("method", method);
        msg.add("params", params);
        for (UnixTransport c : clients) {
            send(c, msg);
        }
    }

    private void serve(UnixTransport c) {
        LineReader reader = new LineReader(c, 1024 * 1024);
        try {
            byte[] line;
            while ((line = reader.next()) != null) {
                JsonObject msg = JsonParser.parseString(new String(line, StandardCharsets.UTF_8)).getAsJsonObject();
                String method = msg.get("method").getAsString();
                JsonObject params = msg.has("params") ? msg.getAsJsonObject("params") : new JsonObject();
                calls.add(new Call(method, params));
                answer(c, msg.get("id"), method, params);
            }
        } catch (IOException e) {
            // client left
        } finally {
            clients.remove(c);
            c.close();
        }
    }

    private void answer(UnixTransport c, JsonElement id, String method, JsonObject p) {
        List<JsonObject> after = new ArrayList<>();
        JsonElement result;
        switch (method) {
            case "app.register" -> {
                List<String> scopes = granted;
                if (scopes == null) {
                    scopes = new ArrayList<>();
                    for (JsonElement s : p.getAsJsonArray("scopes")) {
                        scopes.add(s.getAsJsonObject().get("scope").getAsString());
                    }
                    granted = scopes;
                }
                result = obj("status", "pending", "requestId", "req_1", "expiresAt", 0, "identity", null);
                if (consent != null) {
                    after.add(event("registration.resolved", outcome("req_1", "register", consent, scopes)));
                }
            }
            case "session.hello" -> {
                if (identityChanges.get() > 0) {
                    identityChanges.decrementAndGet();
                    error(c, id, -32004, "RegistrationPending", obj("reason", "identityChanged",
                            "requestId", "req_id", "expiresAt", 0));
                    send(c, event("registration.resolved", outcome("req_id", "identity", identityConsent, null)));
                    return;
                }
                if (helloError != null) {
                    error(c, id, -32000, helloError, obj());
                    return;
                }
                if (!TOKEN.equals(p.get("token").getAsString())) {
                    error(c, id, -32002, "Unauthorized", obj());
                    return;
                }
                result = obj("apiVersion", "1.0.0", "appVersion", "9.9.9", "appId", p.get("appId").getAsString(),
                        "grantedScopes", array(granted), "signedIn", true, "identity", null);
            }
            case "events.subscribe" -> result = obj("topics", p.get("topics"), "positionHz", 1);
            case "app.unregister" -> {
                result = obj();
                reply(c, id, result);
                c.close();
                return;
            }
            default -> result = obj();
        }
        reply(c, id, result);
        for (JsonObject e : after) {
            send(c, e);
        }
    }

    private JsonObject outcome(String requestId, String kind, String status, List<String> scopes) {
        boolean approved = "approved".equals(status);
        return obj("requestId", requestId, "kind", kind, "status", status,
                "token", approved && kind.equals("register") ? TOKEN : null,
                "grantedScopes", approved && scopes != null ? array(scopes) : null,
                "retryAfterMs", approved || "blocked".equals(status) ? null : retryMs);
    }

    private void reply(UnixTransport c, JsonElement id, JsonElement result) {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.add("id", id);
        msg.add("result", result);
        send(c, msg);
    }

    private void error(UnixTransport c, JsonElement id, int code, String name, JsonObject data) {
        data.addProperty("name", name);
        if (!data.has("reason")) {
            data.add("reason", null);
        }
        JsonObject err = obj("code", code, "message", name);
        err.add("data", data);
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.add("id", id);
        msg.add("error", err);
        send(c, msg);
    }

    private static JsonObject event(String method, JsonObject params) {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.addProperty("method", method);
        msg.add("params", params);
        return msg;
    }

    private static void send(UnixTransport c, JsonObject msg) {
        byte[] b = (msg + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            synchronized (c) {
                c.write(b, 0, b.length);
            }
        } catch (IOException e) {
            // client left
        }
    }

    private static JsonArray array(List<String> items) {
        JsonArray a = new JsonArray();
        if (items != null) {
            items.forEach(a::add);
        }
        return a;
    }

    /** JSON object from key, value pairs. */
    public static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = (String) kv[i];
            if (v == null) {
                o.add(k, null);
            } else if (v instanceof JsonElement e) {
                o.add(k, e);
            } else if (v instanceof Number n) {
                o.addProperty(k, n);
            } else if (v instanceof Boolean b) {
                o.addProperty(k, b);
            } else {
                o.addProperty(k, v.toString());
            }
        }
        return o;
    }
}
