package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum PlaybackStatus {
    @SerializedName("playing") PLAYING,
    @SerializedName("paused") PAUSED,
    @SerializedName("buffering") BUFFERING,
    @SerializedName("stopped") STOPPED
}
