package dev.opecko.sonocraft.api.model;

import java.util.List;

/** Result of queue.get: queue summary and one page of items. */
public record QueuePage(long revision, Integer currentIndex, int length, boolean autoplay, List<QueueEntry> items,
                        String nextCursor) {
    public QueueSummary summary() {
        return new QueueSummary(revision, currentIndex, length, autoplay);
    }
}
