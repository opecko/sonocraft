package dev.opecko.sonocraft.api;

/** Method call while the session isn't CONNECTED. */
public final class NotConnectedException extends Exception {
    public NotConnectedException(String method) {
        super(method + ": not connected to Sonolith");
    }
}
