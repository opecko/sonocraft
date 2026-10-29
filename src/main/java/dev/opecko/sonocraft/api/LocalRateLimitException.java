package dev.opecko.sonocraft.api;

/** Call stopped by the client-side rate limit before Sonolith would reject it. */
public final class LocalRateLimitException extends Exception {
    private final RateClass rateClass;
    private final long retryAfterMs;

    public LocalRateLimitException(String method, RateClass rateClass, long retryAfterMs) {
        super(method + ": rate class " + rateClass + " on hold for " + retryAfterMs + " ms");
        this.rateClass = rateClass;
        this.retryAfterMs = retryAfterMs;
    }

    public RateClass rateClass() {
        return rateClass;
    }

    public long retryAfterMs() {
        return retryAfterMs;
    }
}
