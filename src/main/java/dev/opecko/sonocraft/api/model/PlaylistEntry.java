package dev.opecko.sonocraft.api.model;

/** Playlist entry; `entryId` (not the item id) is used for removal. */
public record PlaylistEntry(String entryId, MediaItem item) {
}
