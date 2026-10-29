package dev.opecko.sonocraft.platform.token;

import dev.opecko.sonocraft.Sonocraft;

/**
 * Primary store (secret store) with a fallback file. Reads the primary first, saves to the primary and deletes the
 * fallback; if the primary fails, uses the fallback. Clearing always tries both.
 */
public final class FallbackTokenStore implements TokenStore {
    private final TokenStore primary;
    private final TokenStore fallback;

    public FallbackTokenStore(TokenStore primary, TokenStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String load() throws TokenStoreException {
        try {
            String token = primary.load();
            if (token != null) {
                return token;
            }
        } catch (TokenStoreException e) {
            Sonocraft.LOGGER.warn("{} unavailable, trying {}: {}", primary.describe(), fallback.describe(),
                    e.getMessage());
        }
        return fallback.load();
    }

    @Override
    public void save(String token) throws TokenStoreException {
        try {
            primary.save(token);
        } catch (TokenStoreException e) {
            Sonocraft.LOGGER.warn("{} failed, using {}: {}", primary.describe(), fallback.describe(), e.getMessage());
            fallback.save(token);
            return;
        }
        try {
            fallback.delete();
        } catch (TokenStoreException e) {
            Sonocraft.LOGGER.warn("Cannot remove the old token from {}: {}", fallback.describe(), e.getMessage());
        }
    }

    @Override
    public void delete() throws TokenStoreException {
        TokenStoreException first = null;
        try {
            primary.delete();
        } catch (TokenStoreException e) {
            first = e;
        }
        try {
            fallback.delete();
        } catch (TokenStoreException e) {
            if (first == null) {
                first = e;
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public String describe() {
        return primary.describe() + ", fallback " + fallback.describe();
    }
}
