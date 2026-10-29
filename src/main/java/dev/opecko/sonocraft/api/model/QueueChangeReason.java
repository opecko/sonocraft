package dev.opecko.sonocraft.api.model;

import com.google.gson.annotations.SerializedName;

/** Reason for a queue change. An unknown value arrives as null and is treated as OTHER. */
public enum QueueChangeReason {
    @SerializedName("replaced") REPLACED,
    @SerializedName("added") ADDED,
    @SerializedName("removed") REMOVED,
    @SerializedName("moved") MOVED,
    @SerializedName("cleared") CLEARED,
    @SerializedName("advanced") ADVANCED,
    @SerializedName("autoplay") AUTOPLAY,
    @SerializedName("other") OTHER
}
