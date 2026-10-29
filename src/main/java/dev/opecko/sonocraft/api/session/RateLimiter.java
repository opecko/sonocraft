package dev.opecko.sonocraft.api.session;

import dev.opecko.sonocraft.api.RateClass;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Client-side token buckets per Sonolith class (SPEC §12.1), so the mod never exceeds the limit. After a
 * RateLimited response the class stays blocked for {@code retryAfterMs}.
 */
public final class RateLimiter {
    private final LongSupplier nanoTime;
    private final Map<RateClass, Bucket> buckets = new EnumMap<>(RateClass.class);

    private static final class Bucket {
        double tokens;
        long lastNanos;
        long holdUntilNanos;
    }

    public RateLimiter() {
        this(System::nanoTime);
    }

    public RateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        long now = nanoTime.getAsLong();
        for (RateClass c : RateClass.values()) {
            Bucket b = new Bucket();
            b.tokens = c.burst();
            b.lastNanos = now;
            b.holdUntilNanos = now;
            buckets.put(c, b);
        }
    }

    /** Takes a token; returns 0, or how many ms to wait if the bucket is empty or the class is on hold. */
    public synchronized long tryAcquire(RateClass c) {
        Bucket b = buckets.get(c);
        long now = nanoTime.getAsLong();
        if (now - b.holdUntilNanos < 0) {
            return Math.max(1, (b.holdUntilNanos - now + 999_999) / 1_000_000);
        }
        b.tokens = Math.min(c.burst(), b.tokens + (now - b.lastNanos) / 1e9 * c.perSecond());
        b.lastNanos = now;
        if (b.tokens >= 1) {
            b.tokens -= 1;
            return 0;
        }
        return Math.max(1, (long) Math.ceil((1 - b.tokens) / c.perSecond() * 1000));
    }

    /** Sonolith answered RateLimited: the class waits at least {@code ms}, then one token is available. */
    public synchronized void hold(RateClass c, long ms) {
        Bucket b = buckets.get(c);
        long until = nanoTime.getAsLong() + ms * 1_000_000;
        if (until - b.holdUntilNanos > 0) {
            b.holdUntilNanos = until;
        }
        b.tokens = 1;
        b.lastNanos = b.holdUntilNanos;
    }
}
