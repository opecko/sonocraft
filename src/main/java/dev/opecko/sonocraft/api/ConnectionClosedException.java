package dev.opecko.sonocraft.api;

/** The connection closed before the response arrived. */
public final class ConnectionClosedException extends Exception {
    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
