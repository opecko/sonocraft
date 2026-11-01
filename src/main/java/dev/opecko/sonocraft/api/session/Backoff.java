package dev.opecko.sonocraft.api.session;

import java.util.Random;
import java.util.random.RandomGenerator;

/** Exponential backoff 1 s → 60 s with ±20 % jitter (SPEC §3.4). */
public final class Backoff {
    private final long initialMs;
    private final long maxMs;
    private final double jitter;
    private final RandomGenerator random;
    private long nextMs;

    public Backoff() {
        // Not RandomGenerator.getDefault(): its algorithm lives in the jdk.random module, which
        // trimmed launcher runtimes may leave out (crashes at startup).
        this(1000, 60_000, 0.2, new Random());
    }

    public Backoff(long initialMs, long maxMs, double jitter, RandomGenerator random) {
        this.initialMs = initialMs;
        this.maxMs = maxMs;
        this.jitter = jitter;
        this.random = random;
        this.nextMs = initialMs;
    }

    /** Next delay, at least {@code atLeastMs}. */
    public synchronized long next(long atLeastMs) {
        long base = nextMs;
        nextMs = Math.min(nextMs * 2, maxMs);
        double factor = 1 + (random.nextDouble() * 2 - 1) * jitter;
        return Math.max(Math.round(base * factor), atLeastMs);
    }

    public synchronized void reset() {
        nextMs = initialMs;
    }
}
