package dev.opecko.sonocraft.api.model;

public record LyricsLineEvent(String trackId, Integer lineIndex, String text, Long startMs, Long endMs,
                              long positionMs) {
}
