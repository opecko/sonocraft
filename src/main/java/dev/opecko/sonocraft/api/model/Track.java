package dev.opecko.sonocraft.api.model;

import java.util.List;
import java.util.stream.Collectors;

/** Track or music video (`video`). `rating` is null without library:read. */
public record Track(String id, String title, List<ArtistCredit> artists, AlbumCredit album, Long durationMs,
                    String thumbnailUrl, boolean explicit, boolean video, Rating rating) implements MediaItem {
    @Override
    public ItemType type() {
        return ItemType.TRACK;
    }

    @Override
    public String displayTitle() {
        return title;
    }

    /** Artist names joined with a separator. */
    public String artistNames(String separator) {
        return artists == null ? "" : artists.stream().map(ArtistCredit::name).filter(n -> n != null && !n.isBlank()).collect(Collectors.joining(separator));
    }
}
