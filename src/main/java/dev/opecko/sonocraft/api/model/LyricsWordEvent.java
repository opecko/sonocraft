package dev.opecko.sonocraft.api.model;

public record LyricsWordEvent(String trackId, int lineIndex, int wordIndex, boolean background, long positionMs) {
}
