package dev.opecko.sonocraft.api.model;

import java.util.List;

public record Album(String id, String title, List<ArtistCredit> artists, AlbumType albumType, Integer year,
                    String thumbnailUrl, boolean explicit) implements Item {
    @Override
    public ItemType type() {
        return ItemType.ALBUM;
    }

    @Override
    public String displayTitle() {
        return title;
    }
}
