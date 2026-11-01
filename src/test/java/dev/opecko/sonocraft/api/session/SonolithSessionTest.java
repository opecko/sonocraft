package dev.opecko.sonocraft.api.session;

import static dev.opecko.sonocraft.testing.FakeSonolith.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.ErrorCode;
import dev.opecko.sonocraft.api.NotConnectedException;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.testing.FakeSonolith;
import dev.opecko.sonocraft.testing.MemoryTokenStore;
import dev.opecko.sonocraft.testing.Wait;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SonolithSessionTest {
    private FakeSonolith fake;
    private MemoryTokenStore tokens;
    private SonolithSession session;
    private final List<SessionStatus> statuses = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<Notice> notices = new CopyOnWriteArrayList<>();

    static AppRegistration registration() {
        Map<Scope, AppRegistration.ScopeSpec> scopes = new LinkedHashMap<>();
        scopes.put(Scope.PLAYER_READ, new AppRegistration.ScopeSpec(true, "To show the current song."));
        scopes.put(Scope.PLAYER_CONTROL, new AppRegistration.ScopeSpec(true, "To control playback with keys."));
        scopes.put(Scope.LYRICS_READ, new AppRegistration.ScopeSpec(false, "To show lyrics."));
        return new AppRegistration("dev.opecko.sonocraft", "Sonocraft", "Test client.", "opecko", null, null, scopes);
    }

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeSonolith();
        fake.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        fake.close();
    }

    private void startSession(String token) {
        tokens = new MemoryTokenStore(token);
        Backoff backoff = new Backoff(50, 200, 0, new Random());
        session = new SonolithSession(registration(), tokens, fake.connector(), backoff, new RateLimiter(),
                System::currentTimeMillis);
        session.addListener(new SessionListener() {
            @Override
            public void onStatus(SessionStatus status) {
                statuses.add(status);
            }

            @Override
            public void onEvent(String method, JsonObject params) {
                events.add(method);
            }

            @Override
            public void onNotice(Notice notice) {
                notices.add(notice);
            }
        });
        session.start();
    }

    private void awaitState(SessionState state) {
        Wait.until(() -> session.status().state() == state, () -> "state " + state + ", now " + session.status());
    }

    private static List<String> topics(JsonObject subscribeParams) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : subscribeParams.getAsJsonArray("topics")) {
            out.add(e.getAsString());
        }
        return out;
    }

    @Test
    void withoutTokenWaitsForTheUser() throws Exception {
        startSession(null);
        Thread.sleep(300);
        assertEquals(SessionState.NOT_CONNECTED, session.status().state());
        assertEquals(0, fake.connections.get(), "must not connect or register on its own");
    }

    @Test
    void registersAfterConnectAndSubscribes() throws Exception {
        startSession(null);
        assertEquals(SonolithSession.ConnectResult.STARTED, session.connect());
        awaitState(SessionState.CONNECTED);
        assertEquals(FakeSonolith.TOKEN, tokens.token());
        Wait.until(() -> fake.methods().contains("events.subscribe"), () -> fake.methods().toString());
        assertEquals(List.of("app.register", "session.hello", "events.subscribe"), fake.methods());

        JsonObject reg = fake.called("app.register").get(0);
        assertEquals("1.0", reg.get("apiVersion").getAsString());
        assertEquals("dev.opecko.sonocraft", reg.get("appId").getAsString());
        JsonArray scopes = reg.getAsJsonArray("scopes");
        assertEquals("player:read", scopes.get(0).getAsJsonObject().get("scope").getAsString());
        assertFalse(scopes.get(2).getAsJsonObject().get("required").getAsBoolean());
        assertEquals(1, fake.connections.get(), "hello on the registering connection");

        assertEquals(List.of("player.state", "player.track", "player.position", "player.volume", "lyrics",
                "lyrics.line", "account"), topics(fake.called("events.subscribe").get(0)));
        assertEquals(Scope.parseAll(List.of("player:read", "player:control", "lyrics:read")), session.status().granted());
        assertTrue(session.status().signedIn());
    }

    @Test
    void deniedRegistrationStartsCooldown() throws Exception {
        fake.consent = "denied";
        fake.retryMs = 60_000;
        startSession(null);
        session.connect();
        awaitState(SessionState.COOLDOWN);
        SessionStatus s = session.status();
        assertEquals(StatusReason.DENIED, s.reason());
        assertTrue(s.remainingMs(System.currentTimeMillis()) > 50_000);
        assertNull(tokens.token());
        assertFalse(fake.methods().contains("session.hello"));

        assertEquals(SonolithSession.ConnectResult.COOLDOWN, session.connect());
        Wait.until(() -> notices.stream().anyMatch(n -> n.kind() == Notice.Kind.REGISTER_COOLDOWN), notices::toString);
        Thread.sleep(200);
        assertEquals(1, fake.called("app.register").size(), "no new request during the cooldown");
    }

    @Test
    void blockedRegistrationStops() {
        fake.consent = "blocked";
        startSession(null);
        session.connect();
        awaitState(SessionState.BLOCKED);
    }

    @Test
    void expiredRegistrationCanBeRetriedAfterItsCooldown() {
        fake.consent = "expired";
        fake.retryMs = 100;
        startSession(null);
        session.connect();
        awaitState(SessionState.COOLDOWN);
        assertEquals(StatusReason.EXPIRED, session.status().reason());
        Wait.until(() -> session.status().remainingMs(System.currentTimeMillis()) == 0, () -> "cooldown");
        fake.consent = "approved";
        assertEquals(SonolithSession.ConnectResult.STARTED, session.connect());
        awaitState(SessionState.CONNECTED);
    }

    @Test
    void connectsWithStoredToken() {
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        assertFalse(fake.methods().contains("app.register"));
    }

    @Test
    void unauthorizedDeletesTokenAndNeverRegistersOnItsOwn() throws Exception {
        startSession("snl_wrong");
        Wait.until(() -> session.status().reason() == StatusReason.ACCESS_ENDED, () -> session.status().toString());
        assertEquals(SessionState.NOT_CONNECTED, session.status().state());
        assertNull(tokens.token());
        Thread.sleep(300);
        assertFalse(fake.methods().contains("app.register"));
        assertEquals(1, fake.called("session.hello").size());
    }

    @Test
    void revokedDeletesTokenAndStops() throws Exception {
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        fake.revoke("removed");
        // Usually the session reads session.revoked. When the socket closes with data still unread, Linux can
        // drop what is queued and the event is lost: then the reconnect gets Unauthorized. Both must end the same.
        Wait.until(() -> session.status().reason() == StatusReason.REVOKED_REMOVED
                || session.status().reason() == StatusReason.ACCESS_ENDED, () -> session.status().toString());
        assertEquals(SessionState.NOT_CONNECTED, session.status().state());
        assertNull(tokens.token());
        int connections = fake.connections.get();
        Thread.sleep(300);
        assertEquals(connections, fake.connections.get(), "no reconnect after the access ended");
        assertTrue(fake.called("app.register").isEmpty(), "never registers again on its own");
    }

    @Test
    void reconnectsWithBackoffWhenSonolithReturns() throws Exception {
        fake.stop();
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.UNAVAILABLE);
        assertEquals(StatusReason.NOT_AVAILABLE, session.status().reason());
        assertTrue(session.status().retryAtMs() > 0);
        fake.start();
        awaitState(SessionState.CONNECTED);
    }

    @Test
    void reconnectsAndResubscribesAfterConnectionLoss() {
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        Wait.until(() -> fake.called("events.subscribe").size() == 1, () -> fake.methods().toString());
        fake.dropConnections();
        Wait.until(() -> statuses.stream().anyMatch(s -> s.reason() == StatusReason.CONNECTION_LOST),
                statuses::toString);
        Wait.until(() -> fake.called("events.subscribe").size() == 2, () -> fake.methods().toString());
        awaitState(SessionState.CONNECTED);
    }

    @Test
    void identityChangeWaitsForApprovalThenSaysHelloAgain() {
        fake.identityChanges.set(1);
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        assertTrue(statuses.stream().anyMatch(s -> s.state() == SessionState.IDENTITY_PENDING));
        assertEquals(2, fake.called("session.hello").size());
        assertEquals(1, fake.connections.get(), "the same connection stays open");
        assertEquals(FakeSonolith.TOKEN, tokens.token());
    }

    @Test
    void deniedIdentityKeepsTokenAndWaitsForTheUser() throws Exception {
        fake.identityChanges.set(1);
        fake.identityConsent = "denied";
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.STOPPED);
        assertEquals(StatusReason.IDENTITY_NOT_APPROVED, session.status().reason());
        assertEquals(FakeSonolith.TOKEN, tokens.token());
        Thread.sleep(300);
        assertEquals(1, fake.connections.get());
        session.connect();
        awaitState(SessionState.CONNECTED);
    }

    @Test
    void disconnectUnregistersAndDeletesToken() throws Exception {
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        assertTrue(session.disconnect().get(5, TimeUnit.SECONDS));
        assertEquals(List.of("app.unregister"), fake.methods().stream().filter(m -> m.startsWith("app.")).toList());
        assertNull(tokens.token());
        awaitState(SessionState.NOT_CONNECTED);
        assertEquals(StatusReason.DISCONNECTED, session.status().reason());
        Thread.sleep(300);
        assertEquals(1, fake.called("session.hello").size(), "no reconnect after disconnect");
    }

    @Test
    void disconnectDeletesTokenEvenWhenSonolithIsDown() throws Exception {
        fake.stop();
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.UNAVAILABLE);
        assertFalse(session.disconnect().get(5, TimeUnit.SECONDS));
        assertNull(tokens.token());
        awaitState(SessionState.NOT_CONNECTED);
        assertEquals(StatusReason.DISCONNECTED_UNREACHABLE, session.status().reason());
    }

    @Test
    void callsCheckConnectionAndScopesLocally() throws Exception {
        fake.granted = List.of("player:read", "player:control");
        startSession(FakeSonolith.TOKEN);
        try {
            session.call("player.toggle", null).get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertInstanceOf(NotConnectedException.class, e.getCause());
        }
        awaitState(SessionState.CONNECTED);
        session.call("player.toggle", null).get(5, TimeUnit.SECONDS);
        try {
            session.call("library.playlists", null).get(5, TimeUnit.SECONDS);
            throw new AssertionError("expected PermissionDenied");
        } catch (ExecutionException e) {
            SonolithException se = assertInstanceOf(SonolithException.class, e.getCause());
            assertEquals(ErrorCode.PERMISSION_DENIED, se.error());
            assertEquals(Scope.LIBRARY_READ, se.scope());
        }
        assertFalse(fake.methods().contains("library.playlists"));
    }

    @Test
    void deliversEventsAndFollowsScopeChanges() {
        fake.granted = List.of("player:read", "player:control");
        startSession(FakeSonolith.TOKEN);
        awaitState(SessionState.CONNECTED);
        Wait.until(() -> fake.called("events.subscribe").size() == 1, () -> fake.methods().toString());
        assertFalse(topics(fake.called("events.subscribe").get(0)).contains("lyrics"));

        fake.push("player.stateChanged", obj("status", "paused"));
        Wait.until(() -> events.contains("player.stateChanged"), events::toString);

        fake.push("session.scopesChanged", obj("grantedScopes", array("player:read", "player:control", "lyrics:read"),
                "added", array("lyrics:read"), "removed", array()));
        Wait.until(() -> fake.called("events.subscribe").size() == 2, () -> fake.methods().toString());
        assertEquals(List.of("lyrics", "lyrics.line"), topics(fake.called("events.subscribe").get(1)));
        assertTrue(session.status().has(Scope.LYRICS_READ));

        fake.push("account.changed", obj("signedIn", false));
        Wait.until(() -> Boolean.FALSE.equals(session.status().signedIn()), () -> session.status().toString());
    }

    private static JsonArray array(String... items) {
        JsonArray a = new JsonArray();
        for (String i : items) {
            a.add(i);
        }
        return a;
    }
}
