package dev.opecko.sonocraft.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.model.PlaybackStatus;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.StatusReason;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerStoreTest {
    private static final String TRACK = """
            {"type":"track","id":"t1","title":"Song","artists":[{"id":null,"name":"A"}],"album":null,
             "durationMs":200000,"thumbnailUrl":null,"explicit":false,"video":false,"rating":null}""";

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static JsonObject state(String status, long pos, long at) {
        return json("""
                {"status":"%s","item":%s,"positionMs":%d,"positionAt":%d,"durationMs":200000,"volume":50,
                 "muted":false,"shuffle":false,"repeat":"off","rate":1,"episodeRate":1,"canGoNext":true,
                 "canGoPrevious":true,"queueIndex":0,"queueLength":1,"autoplay":false}""".formatted(status, TRACK, pos, at));
    }

    @Test
    void interpolatesOnlyWhilePlayingAndClampsToDuration() {
        PlayerStore store = new PlayerStore();
        store.onEvent("player.stateChanged", state("playing", 10_000, 1_000_000));
        PlayerSnapshot s = store.get();
        assertTrue(store.isKnown());
        assertInstanceOf(Track.class, s.item());
        assertEquals(10_000, s.positionAt(1_000_000));
        assertEquals(12_500, s.positionAt(1_002_500));
        assertEquals(200_000, s.positionAt(9_000_000));
        assertEquals(0.0625, s.progressAt(1_002_500));

        store.onEvent("player.stateChanged", state("paused", 30_000, 1_000_000));
        assertEquals(30_000, store.get().positionAt(5_000_000));
    }

    @Test
    void positionEventsCorrectTheClockAndRate() {
        PlayerStore store = new PlayerStore();
        store.onEvent("player.stateChanged", state("playing", 0, 1_000_000));
        store.onEvent("player.position", json("""
                {"positionMs":5000,"positionAt":1_004_000,"durationMs":200000,"rate":2,"status":"playing"}"""
                .replace("_", "")));
        assertEquals(7000, store.get().positionAt(1_005_000));
        store.onEvent("player.volumeChanged", json("{\"volume\":20,\"muted\":true}"));
        assertEquals(20, store.get().volume());
        assertTrue(store.get().muted());
        assertEquals(7000, store.get().positionAt(1_005_000));
    }

    @Test
    void clearsOnDisconnect() {
        PlayerStore store = new PlayerStore();
        store.onEvent("player.stateChanged", state("playing", 0, 1));
        store.onStatus(new SessionStatus(SessionState.UNAVAILABLE, StatusReason.CONNECTION_LOST, 0, Set.of(), null,
                null, null, null, null));
        assertFalse(store.isKnown());
        assertEquals(PlaybackStatus.STOPPED, store.get().status());
    }
}
