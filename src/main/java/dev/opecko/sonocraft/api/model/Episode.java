package dev.opecko.sonocraft.api.model;

/** Podcast episode. `played` and `resumePositionMs` are null without library:read. */
public record Episode(String id, String title, PodcastCredit podcast, String author, Long durationMs,
                      String thumbnailUrl, String publishedText, Boolean played, Long resumePositionMs)
        implements MediaItem {
    @Override
    public ItemType type() {
        return ItemType.EPISODE;
    }

    @Override
    public String displayTitle() {
        return title;
    }
}
