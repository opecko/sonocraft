package dev.opecko.sonocraft.api.session;

/** Sonolith connection state (PLAN §3.3). */
public enum SessionState {
    /** No token: waiting for the user to click "Connect". */
    NOT_CONNECTED,
    CONNECTING,
    /** Sonolith isn't running or has the API disabled; next attempt after backoff. */
    UNAVAILABLE,
    /** app.register is waiting for the user's answer in Sonolith (max 5 min). */
    REGISTERING,
    /** The app identity changed (different Java); the user confirms it in Sonolith. */
    IDENTITY_PENDING,
    CONNECTED,
    /** Denied or expired; can ask again only after {@code retryAt}, and only on user action. */
    COOLDOWN,
    BLOCKED,
    /** Needs the user: unconfirmed new identity or unsupported API version. */
    STOPPED
}
