package dev.opecko.sonocraft.api.session;

/** Why the session is in its state. The UI turns it into localized text ({@code sonocraft.status.<name>}). */
public enum StatusReason {
    NONE,
    NOT_AVAILABLE,
    CONNECTION_LOST,
    WAITING_FOR_APPROVAL,
    IDENTITY_CHANGED,
    DENIED,
    EXPIRED,
    BLOCKED,
    ALREADY_PENDING,
    REQUEST_CANCELLED,
    REQUEST_CLOSED,
    ACCESS_ENDED,
    REVOKED_REMOVED,
    REVOKED_BLOCKED,
    REVOKED_UNREGISTERED,
    REVOKED_REPLACED,
    IDENTITY_NOT_APPROVED,
    UNSUPPORTED_VERSION,
    TOKEN_STORE_FAILED,
    DISCONNECTED,
    DISCONNECTED_UNREACHABLE,
    /** Unexpected error; details in {@link SessionStatus#detail()}. */
    ERROR
}
