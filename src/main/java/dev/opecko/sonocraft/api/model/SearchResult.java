package dev.opecko.sonocraft.api.model;

import java.util.List;

/** Items of types this version doesn't know are null in `items`. */
public record SearchResult(Item topResult, List<Item> items, String nextCursor) {
}
