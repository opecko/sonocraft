package dev.opecko.sonocraft.api.model;

public record PositionEvent(long positionMs, long positionAt, Long durationMs, double rate, PlaybackStatus status) {
}
