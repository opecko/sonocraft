package dev.opecko.sonocraft.api.model;

public record PodcastDetail(String id, String title, String author, String thumbnailUrl, String description) {
    public Podcast podcast() {
        return new Podcast(id, title, author, thumbnailUrl);
    }
}
