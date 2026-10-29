package dev.opecko.sonocraft.theme;

/**
 * Colours derived from one accent (all ARGB, opaque except {@code dim}): {@code accent} for bars and highlights,
 * {@code dim} as a translucent background tint, {@code bright} for hover, {@code onAccent} for text on the accent.
 */
public record AccentPalette(int base, int accent, int dim, int bright, int onAccent) {
    /** Dark background the HUD and UI draw on; the accent keeps at least 3:1 contrast against it (PLAN §8). */
    public static final int BACKGROUND = 0x141414;

    public static AccentPalette of(int rgb) {
        int accent = Colors.ensureContrast(rgb & 0xFFFFFF, BACKGROUND, 3.0);
        int dim = Colors.argb(0x90, Colors.shiftLightness(accent, -0.35));
        int bright = Colors.argb(0xFF, Colors.shiftLightness(accent, 0.1));
        int onAccent = Colors.luminance(accent) > 0.4 ? 0xFF000000 : 0xFFFFFFFF;
        return new AccentPalette(rgb & 0xFFFFFF, Colors.argb(0xFF, accent), dim, bright, onAccent);
    }
}
