package dev.opecko.sonocraft.api.model;

/** Full player state (player.getState, player.stateChanged). */
public record PlayerState(PlaybackStatus status, MediaItem item, long positionMs, long positionAt, Long durationMs,
                          int volume, boolean muted, boolean shuffle, RepeatMode repeat, double rate,
                          double episodeRate, boolean canGoNext, boolean canGoPrevious, Integer queueIndex,
                          int queueLength, boolean autoplay) {
}
