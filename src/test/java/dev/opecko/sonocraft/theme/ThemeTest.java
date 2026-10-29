package dev.opecko.sonocraft.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThemeTest {
    @Test
    void oklabRoundTrip() {
        for (int rgb : new int[] {0x000000, 0xFFFFFF, 0xE94560, 0x2040E0, 0x7F7F7F, 0x00FF00}) {
            double[] lab = Colors.toOklab(rgb);
            int back = Colors.fromOklab(lab[0], lab[1], lab[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs((rgb >> shift & 0xFF) - (back >> shift & 0xFF)) <= 1,
                        Integer.toHexString(rgb) + " -> " + Integer.toHexString(back));
            }
        }
        assertEquals(0xE94560, Colors.lerp(0x000000, 0xE94560, 1));
        assertEquals(21.0, Colors.contrast(0x000000, 0xFFFFFF), 0.01);
    }

    @Test
    void darkAccentIsLiftedToReadableContrast() {
        AccentPalette p = AccentPalette.of(0x200040);
        assertTrue(Colors.contrast(p.accent() & 0xFFFFFF, AccentPalette.BACKGROUND) >= 3.0);
        assertEquals(0x200040, p.base());
        assertEquals(0xFF, p.accent() >>> 24);
        assertEquals(0xFFFFFFFF, AccentPalette.of(0x200060).onAccent());
        assertEquals(0xFF000000, AccentPalette.of(0xFFE060).onAccent());
    }

    @Test
    void modesAndFallbacks() {
        ThemeManager t = new ThemeManager();
        t.configure(ThemeManager.Mode.ART_OR_MANUAL, 0x112233, 0, 0);
        assertEquals(0x112233, t.palette(0).base());
        t.setArtColor(0x2040E0, 0);
        assertEquals(0x2040E0, t.palette(0).base());
        t.setArtColor(-1, 0);
        assertEquals(0x112233, t.palette(0).base());

        t.configure(ThemeManager.Mode.ART, 0x112233, 0, 0);
        assertEquals(ThemeManager.NEUTRAL, t.palette(0).base());
        t.configure(ThemeManager.Mode.MANUAL, 0x445566, 0, 0);
        t.setArtColor(0x2040E0, 0);
        assertEquals(0x445566, t.palette(0).base());
    }

    @Test
    void transitionsSmoothlyThenSettles() {
        ThemeManager t = new ThemeManager();
        t.configure(ThemeManager.Mode.MANUAL, 0x000000, 400, 0);
        assertEquals(0x000000, t.palette(1000).base());
        t.configure(ThemeManager.Mode.MANUAL, 0xFFFFFF, 400, 1000);
        int mid = t.palette(1200).base();
        assertNotEquals(0x000000, mid);
        assertNotEquals(0xFFFFFF, mid);
        AccentPalette end = t.palette(1400);
        assertEquals(0xFFFFFF, end.base());
        assertSame(end, t.palette(5000), "no new palette per frame after the transition");
    }
}
