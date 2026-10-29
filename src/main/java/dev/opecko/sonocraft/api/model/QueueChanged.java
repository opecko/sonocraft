package dev.opecko.sonocraft.api.model;

public record QueueChanged(long revision, Integer currentIndex, int length, boolean autoplay,
                           QueueChangeReason reason) {
    public QueueSummary summary() {
        return new QueueSummary(revision, currentIndex, length, autoplay);
    }
}
