package dev.opecko.sonocraft.api.model;

import java.util.List;

public record SearchSuggestions(List<String> queries, List<Item> items) {
}
