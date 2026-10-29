package dev.opecko.sonocraft.api.model;

public record QueueEntry(int index, String entryId, MediaItem item, boolean autoplay) {
}
