package dev.opecko.sonocraft.api;

/** Sonolith rate limit classes (SPEC §12.1): token bucket with a refill rate and burst size. */
public enum RateClass {
    LOCAL(50, 100),
    CONTROL(10, 20),
    NETWORK(2, 10),
    ACCOUNT(0.5, 5),
    AUTH(1, 5);

    private final double perSecond;
    private final int burst;

    RateClass(double perSecond, int burst) {
        this.perSecond = perSecond;
        this.burst = burst;
    }

    public double perSecond() {
        return perSecond;
    }

    public int burst() {
        return burst;
    }
}
