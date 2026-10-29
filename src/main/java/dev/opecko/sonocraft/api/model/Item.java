package dev.opecko.sonocraft.api.model;

/** Any YouTube Music item; the concrete type is given by the `type` field. */
public sealed interface Item permits MediaItem, Album, Playlist, Artist, Podcast {
    ItemType type();

    String id();

    /** Display title (name for an artist). */
    String displayTitle();

    String thumbnailUrl();

    default Ref ref() {
        return new Ref(type(), id());
    }
}
