package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum LyricsTier {
    @SerializedName("word") WORD,
    @SerializedName("line") LINE,
    @SerializedName("unsynced") UNSYNCED
}
