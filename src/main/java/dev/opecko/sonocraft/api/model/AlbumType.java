package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum AlbumType {
    @SerializedName("album") ALBUM,
    @SerializedName("single") SINGLE,
    @SerializedName("ep") EP
}
