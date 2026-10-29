package dev.opecko.sonocraft.api.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.opecko.sonocraft.api.RateClass;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class BackoffAndRateLimiterTest {
    @Test
    void backoffDoublesUpToTheCapWithJitter() {
        Backoff b = new Backoff(1000, 60_000, 0.2, RandomGenerator.getDefault());
        long[] bases = {1000, 2000, 4000, 8000, 16_000, 32_000, 60_000, 60_000};
        for (long base : bases) {
            long d = b.next(0);
            assertTrue(d >= base * 0.8 - 1 && d <= base * 1.2 + 1, base + " -> " + d);
        }
        b.reset();
        long d = b.next(0);
        assertTrue(d <= 1201);
        assertEquals(5000, new Backoff(1000, 60_000, 0, RandomGenerator.getDefault()).next(5000));
    }

    @Test
    void bucketAllowsBurstThenRefills() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter r = new RateLimiter(now::get);
        for (int i = 0; i < RateClass.CONTROL.burst(); i++) {
            assertEquals(0, r.tryAcquire(RateClass.CONTROL));
        }
        long wait = r.tryAcquire(RateClass.CONTROL);
        assertTrue(wait > 0 && wait <= 100, "wait " + wait);
        now.addAndGet(100_000_000); // 100 ms = 1 token at 10/s
        assertEquals(0, r.tryAcquire(RateClass.CONTROL));
        // other classes are independent
        assertEquals(0, r.tryAcquire(RateClass.NETWORK));
    }

    @Test
    void holdBlocksTheClassForRetryAfter() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter r = new RateLimiter(now::get);
        r.hold(RateClass.ACCOUNT, 1500);
        assertEquals(1500, r.tryAcquire(RateClass.ACCOUNT));
        now.addAndGet(1_500_000_000L);
        // after the hold one call passes, then the bucket refills at 0.5/s
        assertEquals(0, r.tryAcquire(RateClass.ACCOUNT));
        assertEquals(2000, r.tryAcquire(RateClass.ACCOUNT));
        now.addAndGet(2_000_000_000L);
        assertEquals(0, r.tryAcquire(RateClass.ACCOUNT));
    }
}
