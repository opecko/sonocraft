package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum RepeatMode {
    @SerializedName("off") OFF,
    @SerializedName("all") ALL,
    @SerializedName("one") ONE;

    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
