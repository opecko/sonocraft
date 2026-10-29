package dev.opecko.sonocraft.state;

import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.PlaybackStatus;
import dev.opecko.sonocraft.api.model.RepeatMode;

/**
 * Immutable player state for the render thread. Position is {@code positionMs} at time {@code positionAt} (epoch ms);
 * {@link #positionAt(long)} gives the current position.
 */
public record PlayerSnapshot(PlaybackStatus status, MediaItem item, long positionMs, long positionAt, Long durationMs,
                             double rate, int volume, boolean muted, boolean shuffle, RepeatMode repeat,
                             boolean canGoNext, boolean canGoPrevious) {
    public static final PlayerSnapshot EMPTY = new PlayerSnapshot(PlaybackStatus.STOPPED, null, 0, 0, null, 1, 100,
            false, false, RepeatMode.OFF, false, false);

    public boolean isPlaying() {
        return status == PlaybackStatus.PLAYING;
    }

    /**
     * Interpolated position: {@code positionMs + (now - positionAt) * rate}, only while playing; clamped to the duration.
     */
    public long positionAt(long nowMs) {
        long pos = positionMs;
        if (status == PlaybackStatus.PLAYING && positionAt > 0) {
            pos += Math.round(Math.max(0, nowMs - positionAt) * rate);
        }
        if (durationMs != null && durationMs > 0) {
            pos = Math.min(pos, durationMs);
        }
        return Math.max(0, pos);
    }

    /** Progress 0–1, or -1 without a known duration. */
    public double progressAt(long nowMs) {
        return durationMs == null || durationMs <= 0 ? -1 : (double) positionAt(nowMs) / durationMs;
    }
}
