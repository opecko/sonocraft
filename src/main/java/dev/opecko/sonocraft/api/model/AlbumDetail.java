package dev.opecko.sonocraft.api.model;

import java.util.List;

/** Album with all its tracks (browse.album). */
public record AlbumDetail(String id, String title, List<ArtistCredit> artists, AlbumType albumType, Integer year,
                          String thumbnailUrl, boolean explicit, String description, int trackCount,
                          Long durationMs, List<Track> tracks) {
    public Album album() {
        return new Album(id, title, artists, albumType, year, thumbnailUrl, explicit);
    }
}
