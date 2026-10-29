package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Shelf on an artist page. `items` may contain null for types this version doesn't know. */
public record ArtistSection(Kind kind, String title, List<Item> items) {
    public enum Kind {
        @SerializedName("songs") SONGS,
        @SerializedName("albums") ALBUMS,
        @SerializedName("singles") SINGLES,
        @SerializedName("videos") VIDEOS,
        @SerializedName("playlists") PLAYLISTS,
        @SerializedName("episodes") EPISODES,
        @SerializedName("related") RELATED,
        @SerializedName("other") OTHER
    }
}
