package dev.opecko.sonocraft.api;

/** Sonolith isn't running or has the API disabled (SPEC §3.4). There's no way to tell which. */
public final class NotAvailableException extends Exception {
    public NotAvailableException(String message) {
        super(message);
    }

    public NotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
