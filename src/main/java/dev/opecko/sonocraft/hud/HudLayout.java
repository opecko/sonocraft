package dev.opecko.sonocraft.hud;

import dev.opecko.sonocraft.config.HudConfig;
import java.util.function.ToIntFunction;

/**
 * Where every HUD element goes. {@link #compute} lays the panel out in its own coordinates (unscaled, origin at the
 * panel's top-left); {@link #place} puts the scaled panel on the screen. Reused every frame, so it holds plain
 * fields and allocates nothing. Used by the HUD and the HUD editor.
 */
public final class HudLayout {
    public static final int FONT_HEIGHT = 9;
    private static final int LINE_GAP = 1;
    /** Space between the status icon and the title. */
    public static final int ICON_GAP = 4;

    /** One text line: where it starts, how much of it is shown and whether it has to scroll. */
    public static final class Line {
        public boolean visible;
        public int x;
        public int y;
        public float scale;
        /** Full text width in panel pixels (scaled by the line's scale). */
        public int textWidth;
        /** Width shown, at most {@link HudConfig#maxTextWidth}. */
        public int width;
        public int height;

        public boolean overflows() {
            return textWidth > width;
        }
    }

    public int width;
    public int height;

    public boolean art;
    public int artX;
    public int artY;
    public int artSize;

    public boolean icon;
    public int iconX;
    public int iconY;
    public int iconSize;

    public final Line title = new Line();
    public final Line artist = new Line();
    public final Line album = new Line();
    public final Line time = new Line();
    private final Line[] lines = {title, artist, album, time};

    public boolean bar;
    public int barX;
    public int barY;
    public int barWidth;
    public int barHeight;

    /** Lyrics block: rows of {@link #lyricsRowHeight}, 0 rows = no block. */
    public int lyricsRows;
    public int lyricsX;
    public int lyricsY;
    public int lyricsWidth;
    public int lyricsRowHeight;

    /** Screen position of the scaled panel's top-left corner and the scale. */
    public float screenX;
    public float screenY;
    public float scale;

    /**
     * Lays out the panel for these texts (null or empty = line hidden). {@code measure} gives the width of a string
     * at scale 1.
     */
    public void compute(HudConfig c, String titleText, String artistText, String albumText, String timeText,
                        ToIntFunction<String> measure) {
        compute(c, titleText, artistText, albumText, timeText, 0, measure);
    }

    /** As above, with a lyrics block of {@code lyricsRows} lines under everything else (0 = none). */
    public void compute(HudConfig c, String titleText, String artistText, String albumText, String timeText,
                        int lyricsRows, ToIntFunction<String> measure) {
        int p = c.padding;
        art = c.art.enabled;
        artSize = art ? c.art.size : 0;
        icon = c.icon.enabled;
        iconSize = icon ? c.icon.size : 0;

        // line sizes; x and y come later
        int iconPart = icon ? iconSize + ICON_GAP : 0;
        measure(title, c.title.enabled, titleText, c.title.scale, c.maxTextWidth - iconPart, measure);
        title.height = Math.max(title.height, iconSize);
        if (icon && !title.visible) {
            title.visible = true; // the icon alone still takes the first line
            title.width = 0;
            title.textWidth = 0;
            title.height = iconSize;
        }
        measure(artist, c.artist.enabled, artistText, c.artist.scale, c.maxTextWidth, measure);
        measure(album, c.album.enabled, albumText, c.album.scale, c.maxTextWidth, measure);
        measure(time, c.time.enabled, timeText, c.time.scale, c.maxTextWidth, measure);
        bar = c.bar.enabled;
        barHeight = bar ? c.bar.height : 0;

        int column = 0;
        column = Math.max(column, title.visible ? iconPart + title.width : 0);
        column = Math.max(column, artist.visible ? artist.width : 0);
        column = Math.max(column, album.visible ? album.width : 0);
        column = Math.max(column, time.visible ? time.width : 0);
        if (bar && c.bar.width > 0) {
            column = Math.max(column, c.bar.width);
        }
        if (bar && column == 0) {
            column = 40;
        }
        barWidth = bar ? (c.bar.width > 0 ? c.bar.width : column) : 0;

        int columnHeight = 0;
        for (Line l : lines) {
            if (l.visible) {
                columnHeight += (columnHeight > 0 ? LINE_GAP : 0) + l.height;
            }
        }
        if (bar) {
            columnHeight += (columnHeight > 0 ? 2 : 0) + barHeight;
        }

        int columnX;
        int columnY;
        int columnWidth;
        if (c.orientation == HudConfig.Orientation.VERTICAL) {
            int inner = Math.max(artSize, column);
            width = inner + 2 * p;
            columnX = p;
            columnWidth = inner;
            artX = p + (inner - artSize) / 2;
            artY = p;
            columnY = p + (art && columnHeight > 0 ? artSize + c.gap : artSize);
            height = columnY + columnHeight + p;
        } else {
            columnX = p + (art && column > 0 ? artSize + c.gap : artSize);
            columnWidth = column;
            width = columnX + column + p;
            int inner = Math.max(artSize, columnHeight);
            height = inner + 2 * p;
            artX = p;
            artY = p + (inner - artSize) / 2;
            columnY = p + (inner - columnHeight) / 2;
        }

        int y = columnY;
        boolean first = true;
        for (Line l : lines) {
            if (!l.visible) {
                continue;
            }
            y += first ? 0 : LINE_GAP;
            first = false;
            int lineWidth = l == title ? iconPart + l.width : l.width;
            int x = columnX + align(c.textAlign, columnWidth - lineWidth);
            if (l == title) {
                iconX = x;
                iconY = y + (l.height - iconSize) / 2;
                l.x = x + iconPart;
                l.y = y + (l.height - Math.round(FONT_HEIGHT * l.scale)) / 2;
            } else {
                l.x = x;
                l.y = y;
            }
            y += l.height;
        }
        barX = columnX + align(c.textAlign, columnWidth - barWidth);
        barY = y + (first ? 0 : 2);

        this.lyricsRows = lyricsRows;
        if (lyricsRows > 0) {
            lyricsRowHeight = Math.round((FONT_HEIGHT + 1) * c.lyrics.scale);
            int inner = width - 2 * p;
            lyricsWidth = c.lyrics.width > 0 ? c.lyrics.width : Math.max(inner, 140);
            lyricsX = p;
            lyricsY = height - p + c.gap;
            width = Math.max(width, lyricsWidth + 2 * p);
            height = lyricsY + lyricsRows * lyricsRowHeight + p;
        }
    }

    private static int align(HudConfig.Align a, int free) {
        return switch (a) {
            case LEFT -> 0;
            case CENTER -> free / 2;
            case RIGHT -> free;
        };
    }

    private static void measure(Line l, boolean enabled, String text, float scale, int max,
                                ToIntFunction<String> measure) {
        l.visible = enabled && text != null && !text.isEmpty();
        l.scale = scale;
        if (!l.visible) {
            l.width = 0;
            l.textWidth = 0;
            l.height = 0;
            return;
        }
        l.textWidth = Math.round(measure.applyAsInt(text) * scale);
        l.width = Math.min(l.textWidth, Math.max(10, max));
        l.height = Math.round(FONT_HEIGHT * scale);
    }

    /** Places the scaled panel on a screen of this size, kept fully on screen. */
    public void place(HudConfig c, int screenWidth, int screenHeight) {
        scale = c.scale;
        float w = width * scale;
        float h = height * scale;
        screenX = c.anchor.x * (screenWidth - w) + c.offsetX;
        screenY = c.anchor.y * (screenHeight - h) + c.offsetY;
        screenX = Math.max(0, Math.min(screenWidth - w, screenX));
        screenY = Math.max(0, Math.min(screenHeight - h, screenY));
    }

    /**
     * Stores a dragged position: picks the anchor nearest to the panel's centre and the offset that keeps the panel
     * there, so it stays in place when the window is resized.
     */
    public static void storePosition(HudConfig c, float x, float y, float w, float h, int screenWidth, int screenHeight) {
        HudConfig.Anchor a = HudConfig.Anchor.nearest((x + w / 2) / screenWidth, (y + h / 2) / screenHeight);
        c.anchor = a;
        c.offsetX = Math.round(x - a.x * (screenWidth - w));
        c.offsetY = Math.round(y - a.y * (screenHeight - h));
    }
}
