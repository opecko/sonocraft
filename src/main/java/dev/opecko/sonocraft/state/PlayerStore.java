package dev.opecko.sonocraft.state;

import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.Json;
import dev.opecko.sonocraft.api.model.PlayerState;
import dev.opecko.sonocraft.api.model.PositionEvent;
import dev.opecko.sonocraft.api.model.RepeatMode;
import dev.opecko.sonocraft.api.model.TrackChanged;
import dev.opecko.sonocraft.api.model.VolumeChanged;
import dev.opecko.sonocraft.api.session.SessionListener;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player state from Sonolith events (player.state, player.track, player.position, player.volume). The snapshot is
 * swapped atomically; the render thread reads it without locks. Cleared when the connection is lost (events aren't
 * replayed, snapshots arrive again after reconnect).
 */
public final class PlayerStore implements SessionListener {
    private final AtomicReference<PlayerSnapshot> snapshot = new AtomicReference<>(PlayerSnapshot.EMPTY);
    private volatile boolean known;

    public PlayerSnapshot get() {
        return snapshot.get();
    }

    /** False until the first state arrives (or after disconnect). */
    public boolean isKnown() {
        return known;
    }

    @Override
    public void onStatus(SessionStatus status) {
        if (status.state() != SessionState.CONNECTED) {
            known = false;
            snapshot.set(PlayerSnapshot.EMPTY);
        }
    }

    @Override
    public void onEvent(String method, JsonObject params) {
        switch (method) {
            case "player.stateChanged" -> {
                PlayerState s = Json.parse(params, PlayerState.class);
                if (s != null) {
                    snapshot.set(new PlayerSnapshot(s.status(), s.item(), s.positionMs(), s.positionAt(),
                            s.durationMs(), s.rate(), s.volume(), s.muted(), s.shuffle(),
                            s.repeat() != null ? s.repeat() : RepeatMode.OFF, s.canGoNext(), s.canGoPrevious()));
                    known = true;
                }
            }
            case "player.trackChanged" -> {
                TrackChanged t = Json.parse(params, TrackChanged.class);
                if (t != null) {
                    snapshot.updateAndGet(p -> new PlayerSnapshot(p.status(), t.item(), 0, System.currentTimeMillis(),
                            t.item() != null ? t.item().durationMs() : null, p.rate(), p.volume(), p.muted(),
                            p.shuffle(), p.repeat(), p.canGoNext(), p.canGoPrevious()));
                }
            }
            case "player.position" -> {
                PositionEvent e = Json.parse(params, PositionEvent.class);
                if (e != null) {
                    snapshot.updateAndGet(p -> new PlayerSnapshot(e.status() != null ? e.status() : p.status(),
                            p.item(), e.positionMs(), e.positionAt(), e.durationMs(), e.rate(), p.volume(), p.muted(),
                            p.shuffle(), p.repeat(), p.canGoNext(), p.canGoPrevious()));
                }
            }
            case "player.volumeChanged" -> {
                VolumeChanged v = Json.parse(params, VolumeChanged.class);
                if (v != null) {
                    snapshot.updateAndGet(p -> new PlayerSnapshot(p.status(), p.item(), p.positionMs(),
                            p.positionAt(), p.durationMs(), p.rate(), v.volume(), v.muted(), p.shuffle(), p.repeat(),
                            p.canGoNext(), p.canGoPrevious()));
                }
            }
            default -> {
            }
        }
    }
}
