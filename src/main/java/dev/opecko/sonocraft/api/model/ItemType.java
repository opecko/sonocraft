package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum ItemType {
    @SerializedName("track") TRACK,
    @SerializedName("episode") EPISODE,
    @SerializedName("album") ALBUM,
    @SerializedName("playlist") PLAYLIST,
    @SerializedName("artist") ARTIST,
    @SerializedName("podcast") PODCAST;

    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
