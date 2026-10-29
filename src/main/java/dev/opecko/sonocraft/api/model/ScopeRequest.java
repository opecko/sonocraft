package dev.opecko.sonocraft.api.model;

/** Scope request at registration; `reason` is one plain sentence for the consent screen. */
public record ScopeRequest(String scope, boolean required, String reason) {
}
