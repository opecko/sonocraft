package dev.opecko.sonocraft.testing;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class Wait {
    private Wait() {
    }

    /** Waits until the condition holds; otherwise fails with the description. */
    public static void until(BooleanSupplier condition, long timeoutMs, Supplier<String> describe) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out: " + describe.get());
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** Waits up to 15 s: generous, because CI machines and a running game can slow the threads down a lot. */
    public static void until(BooleanSupplier condition, Supplier<String> describe) {
        until(condition, 15_000, describe);
    }
}
