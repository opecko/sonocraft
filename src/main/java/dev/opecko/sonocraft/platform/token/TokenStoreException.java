package dev.opecko.sonocraft.platform.token;

public final class TokenStoreException extends Exception {
    public TokenStoreException(String message) {
        super(message);
    }

    public TokenStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
