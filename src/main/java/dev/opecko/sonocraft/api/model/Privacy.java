package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

public enum Privacy {
    @SerializedName("public") PUBLIC,
    @SerializedName("unlisted") UNLISTED,
    @SerializedName("private") PRIVATE;

    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
