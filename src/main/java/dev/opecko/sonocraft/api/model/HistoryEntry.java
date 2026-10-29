package dev.opecko.sonocraft.api.model;

/** `sectionText` is for display only ("Today"), never parsed. */
public record HistoryEntry(MediaItem item, String sectionText) {
}
