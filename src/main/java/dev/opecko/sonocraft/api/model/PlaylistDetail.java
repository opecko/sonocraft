package dev.opecko.sonocraft.api.model;

public record PlaylistDetail(String id, String title, String author, Integer trackCount, String thumbnailUrl,
                             Boolean editable, String description, Privacy privacy) {
    public Playlist playlist() {
        return new Playlist(id, title, author, trackCount, thumbnailUrl, editable);
    }
}
