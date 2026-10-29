package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public record LyricsResult(String trackId, Status status, Lyrics lyrics) {
    public enum Status {
        @SerializedName("found") FOUND,
        @SerializedName("notFound") NOT_FOUND,
        @SerializedName("instrumental") INSTRUMENTAL
    }
}
