package dev.opecko.sonocraft.api.model;

/** Reference to an item. The id is opaque and always kept together with the type (SPEC §13.1). */
public record Ref(ItemType type, String id) {
}
