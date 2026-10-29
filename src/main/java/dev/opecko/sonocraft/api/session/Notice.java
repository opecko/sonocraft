package dev.opecko.sonocraft.api.session;

/** One-off notice for the user (toast). {@code retryMs} is set for countdowns, otherwise 0. */
public record Notice(Kind kind, long retryMs) {
    public enum Kind {
        SCOPES_REQUESTED,
        SCOPES_ALREADY_GRANTED,
        SCOPES_GRANTED,
        SCOPES_DENIED,
        SCOPES_BLOCKED,
        SCOPES_EXPIRED,
        SCOPES_COOLDOWN,
        REGISTER_COOLDOWN,
        SUBSCRIBE_FAILED
    }
}
