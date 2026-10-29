package dev.opecko.sonocraft.api.model;

public record QueueAddResult(int added, boolean truncated, int limit, QueueSummary queue) {
}
