package dev.opecko.sonocraft.platform.token;

/**
 * Client token storage (SPEC §7.2). The token is never logged or shown. Methods may block (they spawn
 * processes, touch the disk), so they're called only from the IO thread.
 */
public interface TokenStore {
    /** The stored token, or null. An exception means the store can't be read right now (not that the token is missing). */
    String load() throws TokenStoreException;

    void save(String token) throws TokenStoreException;

    void delete() throws TokenStoreException;

    /** Short description for the log (without the token). */
    String describe();
}
