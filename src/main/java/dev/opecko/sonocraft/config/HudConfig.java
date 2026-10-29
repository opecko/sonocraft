package dev.opecko.sonocraft.config;

/**
 * Now Playing HUD settings (PLAN §5). Sizes are GUI pixels before {@link #scale}. The panel's anchor point sits on
 * the same point of the screen, moved by {@link #offsetX}/{@link #offsetY}.
 */
public final class HudConfig {
    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
        MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
        BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

        public final float x;
        public final float y;

        Anchor(float x, float y) {
            this.x = x;
            this.y = y;
        }

        /** The anchor whose third of the screen contains the point. */
        public static Anchor nearest(float fx, float fy) {
            int col = fx < 1 / 3f ? 0 : fx < 2 / 3f ? 1 : 2;
            int row = fy < 1 / 3f ? 0 : fy < 2 / 3f ? 1 : 2;
            return values()[row * 3 + col];
        }
    }

    public enum Orientation { HORIZONTAL, VERTICAL }

    public enum Align { LEFT, CENTER, RIGHT }

    public enum Background { NONE, COLOR, ACCENT }

    public enum TimeFormat {
        /** 1:23 / 3:45 */
        ELAPSED_TOTAL,
        /** 1:23 / -2:22 */
        ELAPSED_REMAINING,
        /** 3:45 */
        TOTAL
    }

    public enum Preset { MINIMAL, COMPACT, FULL }

    public static final class Text {
        public boolean enabled;
        public float scale = 1f;

        public Text(boolean enabled) {
            this.enabled = enabled;
        }

        public Text() {
            this(true);
        }
    }

    public static final class Art {
        public boolean enabled = true;
        public int size = 36;
        /** Corner radius in percent of the size (0 = square, 50 = circle). */
        public int rounding = 10;
        public boolean shadow = true;
    }

    public static final class Icon {
        public boolean enabled = true;
        public int size = 8;
    }

    public static final class Time {
        public boolean enabled = true;
        public float scale = 1f;
        public TimeFormat format = TimeFormat.ELAPSED_TOTAL;
    }

    public static final class Lyrics {
        public boolean enabled = true;
        /** Lines shown above and below the one being sung. */
        public int linesBefore = 0;
        public int linesAfter = 1;
        public float scale = 1f;
        /** Colour the sung part of the line word by word (needs word-timed lyrics). */
        public boolean highlightWords = true;
        /** Scroll to the next line instead of jumping. */
        public boolean smooth = true;
        /** 0 = as wide as the panel (at least 140 px). */
        public int width = 0;
    }

    public static final class Bar {
        public boolean enabled = true;
        /** 0 = as wide as the text column. */
        public int width = 0;
        public int height = 2;
    }

    public boolean enabled = true;
    public SonocraftConfig.StatusVisibility connectionStatus = SonocraftConfig.StatusVisibility.ON_ERROR;

    public Anchor anchor = Anchor.TOP_LEFT;
    public int offsetX = 4;
    public int offsetY = 4;
    public float scale = 1f;
    public Orientation orientation = Orientation.HORIZONTAL;
    public Align textAlign = Align.LEFT;
    public int padding = 4;
    /** Space between the artwork and the text. */
    public int gap = 5;
    /** Longer lines scroll (or are cut with "…" when {@link #marqueeSpeed} is 0). */
    public int maxTextWidth = 160;
    /** Pixels per second; 0 = no scrolling. */
    public int marqueeSpeed = 25;

    public Background background = Background.COLOR;
    /** ARGB. */
    public int backgroundColor = 0x90000000;
    public boolean border = false;

    public boolean hideWhenNothingPlaying = true;
    /** Hide after this many seconds of pause; 0 = never. */
    public int hideAfterPausedSeconds = 0;
    /** Hide while a screen (inventory, menu…) is open; chat doesn't count. */
    public boolean hideInScreens = false;

    public Art art = new Art();
    public Icon icon = new Icon();
    public Text title = new Text();
    public Text artist = new Text();
    public Text album = new Text();
    public Time time = new Time();
    public Bar bar = new Bar();
    public Lyrics lyrics = new Lyrics();

    /** Sets the elements and spacing of a preset; position, colours and visibility rules stay. */
    public void apply(Preset preset) {
        Art a = new Art();
        art = a;
        icon = new Icon();
        title = new Text();
        artist = new Text();
        album = new Text();
        time = new Time();
        bar = new Bar();
        lyrics = new Lyrics();
        padding = 4;
        gap = 5;
        orientation = Orientation.HORIZONTAL;
        switch (preset) {
            case MINIMAL -> {
                art.enabled = false;
                artist.enabled = false;
                album.enabled = false;
                time.enabled = false;
                bar.enabled = false;
                lyrics.enabled = false;
                padding = 3;
            }
            case COMPACT -> {
                art.size = 24;
                album.enabled = false;
                time.enabled = false;
                lyrics.enabled = false;
            }
            case FULL -> {
                lyrics.linesBefore = 1;
                lyrics.linesAfter = 1;
            }
        }
    }

    /** Repairs values from an old or hand-edited file: unknown enum names become null in Gson. */
    void normalize() {
        if (connectionStatus == null) {
            connectionStatus = SonocraftConfig.StatusVisibility.ON_ERROR;
        }
        anchor = anchor == null ? Anchor.TOP_LEFT : anchor;
        orientation = orientation == null ? Orientation.HORIZONTAL : orientation;
        textAlign = textAlign == null ? Align.LEFT : textAlign;
        background = background == null ? Background.COLOR : background;
        art = art == null ? new Art() : art;
        icon = icon == null ? new Icon() : icon;
        title = title == null ? new Text() : title;
        artist = artist == null ? new Text() : artist;
        album = album == null ? new Text() : album;
        time = time == null ? new Time() : time;
        time.format = time.format == null ? TimeFormat.ELAPSED_TOTAL : time.format;
        bar = bar == null ? new Bar() : bar;
        lyrics = lyrics == null ? new Lyrics() : lyrics;
        lyrics.linesBefore = clamp(lyrics.linesBefore, 0, 3);
        lyrics.linesAfter = clamp(lyrics.linesAfter, 0, 3);
        lyrics.scale = clamp(lyrics.scale, 0.5f, 2f);
        lyrics.width = clamp(lyrics.width, 0, 400);
        scale = clamp(scale, 0.5f, 3f);
        padding = clamp(padding, 0, 16);
        gap = clamp(gap, 0, 16);
        maxTextWidth = clamp(maxTextWidth, 40, 400);
        marqueeSpeed = clamp(marqueeSpeed, 0, 100);
        hideAfterPausedSeconds = clamp(hideAfterPausedSeconds, 0, 600);
        art.size = clamp(art.size, 16, 128);
        art.rounding = clamp(art.rounding, 0, 50);
        icon.size = clamp(icon.size, 6, 32);
        for (Text t : new Text[] {title, artist, album}) {
            t.scale = clamp(t.scale, 0.5f, 2f);
        }
        time.scale = clamp(time.scale, 0.5f, 2f);
        bar.width = clamp(bar.width, 0, 400);
        bar.height = clamp(bar.height, 1, 8);
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static float clamp(float v, float min, float max) {
        return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }
}
