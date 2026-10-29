package dev.opecko.sonocraft.theme;

/**
 * The current accent palette, shared by HUD and UI (PLAN §8). The accent comes from the configured colour or from
 * the album art, and changes smoothly (OKLab interpolation). Thread-safe; {@link #palette(long)} is cheap and
 * allocation-free once a transition has finished.
 */
public final class ThemeManager {
    public enum Mode {
        MANUAL,
        /** From the album art; neutral grey when the art has no usable colour. */
        ART,
        /** From the album art; the manual colour when the art has none. */
        ART_OR_MANUAL
    }

    /** Used by {@link Mode#ART} when the art has no colour. */
    public static final int NEUTRAL = 0xC8C8C8;

    private Mode mode = Mode.ART_OR_MANUAL;
    private int manual = 0xE94560;
    private long transitionMs = 400;
    /** Accent of the current track's art, or -1 when there is none (yet). */
    private int artColor = -1;

    private int fromBase;
    private int toBase;
    private long startMs;
    private AccentPalette target;
    private AccentPalette lastFrame;

    public ThemeManager() {
        toBase = manual;
        fromBase = manual;
        target = AccentPalette.of(manual);
        lastFrame = target;
    }

    public synchronized void configure(Mode mode, int manualRgb, long transitionMs, long nowMs) {
        this.mode = mode;
        this.manual = manualRgb & 0xFFFFFF;
        this.transitionMs = Math.max(0, transitionMs);
        retarget(nowMs);
    }

    /** The art of the current track gave this colour, or -1 for none / not loaded. */
    public synchronized void setArtColor(int rgb, long nowMs) {
        artColor = rgb < 0 ? -1 : rgb & 0xFFFFFF;
        retarget(nowMs);
    }

    private int wantedBase() {
        return switch (mode) {
            case MANUAL -> manual;
            case ART -> artColor >= 0 ? artColor : NEUTRAL;
            case ART_OR_MANUAL -> artColor >= 0 ? artColor : manual;
        };
    }

    private void retarget(long nowMs) {
        int wanted = wantedBase();
        if (wanted == toBase) {
            return;
        }
        fromBase = lastFrame.base();
        toBase = wanted;
        startMs = nowMs;
        target = AccentPalette.of(wanted);
    }

    public synchronized AccentPalette palette(long nowMs) {
        if (lastFrame == target) {
            return target;
        }
        double t = transitionMs == 0 ? 1 : (double) (nowMs - startMs) / transitionMs;
        lastFrame = t >= 1 ? target : AccentPalette.of(Colors.lerp(fromBase, toBase, Math.max(0, t)));
        return lastFrame;
    }
}
