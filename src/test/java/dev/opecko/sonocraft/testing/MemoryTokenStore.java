package dev.opecko.sonocraft.testing;

import dev.opecko.sonocraft.platform.token.TokenStore;

public final class MemoryTokenStore implements TokenStore {
    private volatile String token;

    public MemoryTokenStore(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    @Override
    public String load() {
        return token;
    }

    @Override
    public void save(String token) {
        this.token = token;
    }

    @Override
    public void delete() {
        token = null;
    }

    @Override
    public String describe() {
        return "memory";
    }
}
