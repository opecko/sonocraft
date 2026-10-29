package dev.opecko.sonocraft.api.model;

public record Artist(String id, String name, String thumbnailUrl, String subscribersText) implements Item {
    @Override
    public ItemType type() {
        return ItemType.ARTIST;
    }

    @Override
    public String displayTitle() {
        return name;
    }
}
