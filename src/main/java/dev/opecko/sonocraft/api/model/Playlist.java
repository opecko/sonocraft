package dev.opecko.sonocraft.api.model;

public record Playlist(String id, String title, String author, Integer trackCount, String thumbnailUrl,
                       Boolean editable) implements Item {
    @Override
    public ItemType type() {
        return ItemType.PLAYLIST;
    }

    @Override
    public String displayTitle() {
        return title;
    }
}
