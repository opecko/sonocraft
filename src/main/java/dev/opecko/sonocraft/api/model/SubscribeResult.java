package dev.opecko.sonocraft.api.model;

import java.util.List;

public record SubscribeResult(List<String> topics, int positionHz) {
}
