package dev.opecko.sonocraft.api.model;

public record EpisodeDetail(String id, String title, PodcastCredit podcast, String author, Long durationMs,
                            String thumbnailUrl, String publishedText, Boolean played, Long resumePositionMs,
                            String description) {
    public Episode episode() {
        return new Episode(id, title, podcast, author, durationMs, thumbnailUrl, publishedText, played,
                resumePositionMs);
    }
}
