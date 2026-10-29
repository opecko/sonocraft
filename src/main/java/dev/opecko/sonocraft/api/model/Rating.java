package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum Rating {
    @SerializedName("like") LIKE,
    @SerializedName("dislike") DISLIKE,
    @SerializedName("none") NONE
}
