package dev.opecko.sonocraft.api.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.ConnectionClosedException;
import dev.opecko.sonocraft.api.ErrorCode;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.testing.SocketPeer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RpcConnectionTest {
    private SocketPeer peer;
    private RpcConnection conn;
    private final List<String> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        peer = new SocketPeer();
        conn = new RpcConnection(peer.client, (method, params) -> events.add(method + " " + params)).start();
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
        peer.close();
    }

    private static Throwable failure(CompletableFuture<?> f) throws Exception {
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
        throw new AssertionError("expected a failure");
    }

    @Test
    void sendsNamedParamsAndOmitsEmptyParams() throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("positionMs", 1000);
        conn.call("player.seek", p);
        conn.call("player.toggle", null);
        JsonObject first = peer.read();
        assertEquals("2.0", first.get("jsonrpc").getAsString());
        assertEquals("player.seek", first.get("method").getAsString());
        assertEquals(1000, first.getAsJsonObject("params").get("positionMs").getAsInt());
        JsonObject second = peer.read();
        assertEquals("player.toggle", second.get("method").getAsString());
        assertTrue(!second.has("params"));
    }

    @Test
    void matchesResponsesOutOfOrder() throws Exception {
        List<CompletableFuture<JsonElement>> futures = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(conn.call("m" + i, null));
            ids.add(peer.read().get("id").getAsLong());
        }
        for (int i = 2; i >= 0; i--) {
            peer.send("{\"jsonrpc\":\"2.0\",\"id\":" + ids.get(i) + ",\"result\":{\"n\":" + i + ",\"extra\":true}}");
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(i, futures.get(i).get(5, TimeUnit.SECONDS).getAsJsonObject().get("n").getAsInt());
        }
    }

    @Test
    void limitsRequestsInFlight() throws Exception {
        List<CompletableFuture<JsonElement>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(conn.call("m", null));
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < RpcConnection.MAX_IN_FLIGHT; i++) {
            ids.add(peer.read().get("id").getAsLong());
        }
        // the 17th request may go out only after one of the first 16 is answered
        CompletableFuture<JsonObject> seventeenth = CompletableFuture.supplyAsync(() -> {
            try {
                return peer.read();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(200);
        assertTrue(!seventeenth.isDone(), "more than 16 requests in flight");
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":" + ids.get(0) + ",\"result\":{}}");
        assertEquals(17, seventeenth.get(5, TimeUnit.SECONDS).get("id").getAsLong());
        futures.get(0).get(5, TimeUnit.SECONDS);
    }

    @Test
    void mapsErrors() throws Exception {
        CompletableFuture<JsonElement> f = conn.call("player.next", null);
        long id = peer.read().get("id").getAsLong();
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32009,\"message\":\"RateLimited\","
                + "\"data\":{\"name\":\"RateLimited\",\"reason\":null,\"retryAfterMs\":1500}}}");
        SonolithException e = assertInstanceOf(SonolithException.class, failure(f));
        assertEquals(ErrorCode.RATE_LIMITED, e.error());
        assertNull(e.reason());
        assertEquals(1500, e.retryAfterMs());
    }

    @Test
    void unknownErrorNameFallsBackToCode() throws Exception {
        CompletableFuture<JsonElement> f = conn.call("x", null);
        long id = peer.read().get("id").getAsLong();
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32008,\"message\":\"?\"}}");
        assertEquals(ErrorCode.NOT_FOUND, assertInstanceOf(SonolithException.class, failure(f)).error());
    }

    @Test
    void deliversNotificationsAndSurvivesGarbage() throws Exception {
        peer.send("not json at all");
        peer.send("[1,2]");
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"ParseError\"}}");
        peer.send("{\"jsonrpc\":\"2.0\",\"method\":\"player.trackChanged\",\"params\":{\"item\":null}}");
        peer.send("{\"jsonrpc\":\"2.0\",\"method\":\"some.futureEvent\"}");
        long deadline = System.currentTimeMillis() + 5000;
        while (events.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(List.of("player.trackChanged {\"item\":null}", "some.futureEvent {}"), events);
        assertTrue(!conn.isClosed());
    }

    @Test
    void failsPendingCallsWhenClosed() throws Exception {
        CompletableFuture<JsonElement> f = conn.call("x", null);
        peer.read();
        peer.closeServerSide();
        assertInstanceOf(ConnectionClosedException.class, failure(f));
        conn.closed().get(5, TimeUnit.SECONDS);
        assertInstanceOf(ConnectionClosedException.class, failure(conn.call("y", null)));
    }

    @Test
    void closesOnOversizedMessage() throws Exception {
        CompletableFuture<JsonElement> f = conn.call("x", null);
        peer.read();
        StringBuilder big = new StringBuilder("{\"jsonrpc\":\"2.0\",\"method\":\"x\",\"params\":{\"p\":\"");
        big.append("a".repeat(RpcConnection.MAX_MESSAGE));
        big.append("\"}}");
        try {
            peer.send(big.toString());
        } catch (java.io.IOException e) {
            // client closed the connection mid-write
        }
        assertInstanceOf(ConnectionClosedException.class, failure(f));
        assertInstanceOf(LineReader.MessageTooLargeException.class, conn.closed().get(5, TimeUnit.SECONDS));
    }

    @Test
    void timesOutAndFreesTheSlot() throws Exception {
        conn.close();
        peer.close();
        peer = new SocketPeer();
        conn = new RpcConnection(peer.client, (m, p) -> { }, Duration.ofMillis(200)).start();
        CompletableFuture<JsonElement> f = conn.call("slow", null);
        peer.read();
        assertInstanceOf(TimeoutException.class, failure(f));
        // a late response is silently dropped
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        CompletableFuture<JsonElement> g = conn.call("next", null);
        long id = peer.read().get("id").getAsLong();
        peer.send("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":42}");
        assertEquals(42, g.get(5, TimeUnit.SECONDS).getAsInt());
    }
}
