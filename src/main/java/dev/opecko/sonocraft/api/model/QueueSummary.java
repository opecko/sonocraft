package dev.opecko.sonocraft.api.model;

public record QueueSummary(long revision, Integer currentIndex, int length, boolean autoplay) {
}
