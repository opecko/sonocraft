package dev.opecko.sonocraft.api.model;

public record Podcast(String id, String title, String author, String thumbnailUrl) implements Item {
    @Override
    public ItemType type() {
        return ItemType.PODCAST;
    }

    @Override
    public String displayTitle() {
        return title;
    }
}
