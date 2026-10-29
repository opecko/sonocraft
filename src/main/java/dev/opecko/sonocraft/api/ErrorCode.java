package dev.opecko.sonocraft.api;

/** Sonolith API error codes (SPEC §11.1). */
public enum ErrorCode {
    PARSE_ERROR(-32700, "ParseError"),
    INVALID_REQUEST(-32600, "InvalidRequest"),
    METHOD_NOT_FOUND(-32601, "MethodNotFound"),
    INVALID_PARAMS(-32602, "InvalidParams"),
    INTERNAL_ERROR(-32603, "InternalError"),
    NOT_REGISTERED(-32001, "NotRegistered"),
    UNAUTHORIZED(-32002, "Unauthorized"),
    PERMISSION_DENIED(-32003, "PermissionDenied"),
    REGISTRATION_PENDING(-32004, "RegistrationPending"),
    REGISTRATION_DENIED(-32005, "RegistrationDenied"),
    BLOCKED(-32006, "Blocked"),
    NOT_SIGNED_IN(-32007, "NotSignedIn"),
    NOT_FOUND(-32008, "NotFound"),
    RATE_LIMITED(-32009, "RateLimited"),
    UNAVAILABLE(-32010, "Unavailable"),
    TIMEOUT(-32011, "Timeout"),
    SESSION_REQUIRED(-32012, "SessionRequired"),
    UNSUPPORTED_VERSION(-32013, "UnsupportedVersion"),
    /** A code this client version doesn't know. */
    UNKNOWN(0, "Unknown");

    private final int code;
    private final String wireName;

    ErrorCode(int code, String wireName) {
        this.code = code;
        this.wireName = wireName;
    }

    public int code() {
        return code;
    }

    public String wireName() {
        return wireName;
    }

    /** By `data.name`, falling back to the numeric code. */
    public static ErrorCode of(int code, String name) {
        for (ErrorCode c : values()) {
            if (c != UNKNOWN && c.wireName.equals(name)) {
                return c;
            }
        }
        for (ErrorCode c : values()) {
            if (c != UNKNOWN && c.code == code) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
