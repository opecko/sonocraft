package dev.opecko.sonocraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.opecko.sonocraft.config.HudConfig;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    /** Every character is 6 px wide, like most of Minecraft's font. */
    private static final ToIntFunction<String> SIX = s -> s.length() * 6;

    @Test
    void horizontalLayoutPutsArtLeftAndStacksLines() {
        HudConfig c = new HudConfig();
        HudLayout l = new HudLayout();
        l.compute(c, "Solitude Is Bliss", "Tame Impala", "InnerSpeaker", "0:08 / 3:55", SIX);
        assertTrue(l.art);
        assertEquals(c.padding, l.artX);
        int columnX = c.padding + c.art.size + c.gap;
        assertEquals(columnX, l.iconX);
        assertEquals(columnX + c.icon.size + HudLayout.ICON_GAP, l.title.x);
        assertEquals(columnX, l.artist.x);
        assertTrue(l.artist.y > l.title.y && l.album.y > l.artist.y && l.time.y > l.album.y && l.barY > l.time.y);
        int column = Math.max(c.icon.size + HudLayout.ICON_GAP + 17 * 6, 12 * 6);
        assertEquals(column, l.barWidth);
        assertEquals(columnX + column + c.padding, l.width);
        // the text is taller than the art: art centred, column starts at the padding
        assertEquals(c.padding, l.title.y < l.iconY ? l.title.y : l.iconY);
        assertEquals(l.barY + l.barHeight + c.padding, l.height);
    }

    @Test
    void longLinesAreCappedAndMarkedForScrolling() {
        HudConfig c = new HudConfig();
        c.maxTextWidth = 60;
        HudLayout l = new HudLayout();
        l.compute(c, "A very long song title indeed", "Short", null, null, SIX);
        assertTrue(l.title.overflows());
        assertEquals(60 - c.icon.size - HudLayout.ICON_GAP, l.title.width);
        assertFalse(l.artist.overflows());
        assertFalse(l.album.visible);
        assertFalse(l.time.visible);
    }

    @Test
    void minimalPresetHasOnlyIconAndTitle() {
        HudConfig c = new HudConfig();
        c.apply(HudConfig.Preset.MINIMAL);
        HudLayout l = new HudLayout();
        l.compute(c, "Song", "Artist", "Album", "1:00", SIX);
        assertFalse(l.art);
        assertFalse(l.artist.visible);
        assertFalse(l.bar);
        assertEquals(c.padding, l.iconX);
        assertEquals(2 * c.padding + HudLayout.FONT_HEIGHT, l.height);
    }

    @Test
    void verticalLayoutCentresArtAboveText() {
        HudConfig c = new HudConfig();
        c.orientation = HudConfig.Orientation.VERTICAL;
        c.textAlign = HudConfig.Align.CENTER;
        HudLayout l = new HudLayout();
        l.compute(c, "Hi", "Artist name that is wide", null, null, SIX);
        assertEquals(c.padding, l.artY);
        assertTrue(l.title.y > l.artY + l.artSize);
        int inner = l.width - 2 * c.padding;
        assertEquals(c.padding + (inner - l.artSize) / 2, l.artX);
        assertEquals(c.padding + (inner - (c.icon.size + HudLayout.ICON_GAP + 12)) / 2, l.iconX);
    }

    @Test
    void placementFollowsAnchorAndStaysOnScreen() {
        HudConfig c = new HudConfig();
        HudLayout l = new HudLayout();
        l.compute(c, "Song", null, null, null, SIX);
        c.anchor = HudConfig.Anchor.BOTTOM_RIGHT;
        c.offsetX = -4;
        c.offsetY = -4;
        c.scale = 2;
        l.place(c, 400, 300);
        assertEquals(400 - l.width * 2 - 4, l.screenX, 0.001);
        assertEquals(300 - l.height * 2 - 4, l.screenY, 0.001);
        c.offsetX = 500;
        l.place(c, 400, 300);
        assertEquals(400 - l.width * 2, l.screenX, 0.001);
    }

    @Test
    void storedPositionRoundTripsThroughTheNearestAnchor() {
        HudConfig c = new HudConfig();
        HudLayout l = new HudLayout();
        l.compute(c, "Song", "Artist", null, null, SIX);
        float w = l.width;
        float h = l.height;
        HudLayout.storePosition(c, 380 - w, 150 - h / 2, w, h, 400, 300);
        assertEquals(HudConfig.Anchor.MIDDLE_RIGHT, c.anchor);
        l.place(c, 400, 300);
        assertEquals(380 - w, l.screenX, 1);
        assertEquals(150 - h / 2, l.screenY, 1);
    }

    @Test
    void lyricsBlockSitsUnderEverythingAndWidensNarrowPanels() {
        HudConfig c = new HudConfig();
        c.art.enabled = false;
        HudLayout plain = new HudLayout();
        plain.compute(c, "Hi", null, null, null, SIX);
        HudLayout l = new HudLayout();
        l.compute(c, "Hi", null, null, null, 3, SIX);
        assertEquals(3, l.lyricsRows);
        assertEquals(plain.height - c.padding + c.gap, l.lyricsY);
        assertEquals(l.lyricsY + 3 * l.lyricsRowHeight + c.padding, l.height);
        assertEquals(140, l.lyricsWidth, "a narrow panel gets the minimum lyrics width");
        assertEquals(140 + 2 * c.padding, l.width);
        assertEquals(plain.title.x, l.title.x, "the song lines don't move");
    }
}
