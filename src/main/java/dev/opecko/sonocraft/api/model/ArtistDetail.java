package dev.opecko.sonocraft.api.model;

import java.util.List;

public record ArtistDetail(String id, String name, String thumbnailUrl, String subscribersText, String description,
                           List<ArtistSection> sections) {
    public Artist artist() {
        return new Artist(id, name, thumbnailUrl, subscribersText);
    }
}
