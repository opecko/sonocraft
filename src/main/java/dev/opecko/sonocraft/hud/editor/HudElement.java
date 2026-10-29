package dev.opecko.sonocraft.hud.editor;

import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.config.SonocraftConfig;
import dev.opecko.sonocraft.hud.HudLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * The parts of the HUD the editor can select: where each one is in the layout, whether it is shown, and its
 * settings. {@link #PANEL} is the panel as a whole (layout, spacing, background).
 */
public enum HudElement {
    ART("elements.art"),
    ICON("elements.icon"),
    TITLE("elements.title"),
    ARTIST("elements.artist"),
    ALBUM("elements.album"),
    TIME("elements.time"),
    BAR("elements.bar"),
    LYRICS("elements.lyrics"),
    PANEL("hud.panel");

    private final String key;

    HudElement(String key) {
        this.key = key;
    }

    public Component label() {
        return t(key);
    }

    private static Component t(String key) {
        return Component.translatable("sonocraft.config." + key);
    }

    /** Whether the element is switched on (PANEL: always). */
    public boolean enabled(HudConfig c) {
        return switch (this) {
            case ART -> c.art.enabled;
            case ICON -> c.icon.enabled;
            case TITLE -> c.title.enabled;
            case ARTIST -> c.artist.enabled;
            case ALBUM -> c.album.enabled;
            case TIME -> c.time.enabled;
            case BAR -> c.bar.enabled;
            case LYRICS -> c.lyrics.enabled;
            case PANEL -> true;
        };
    }

    public void setEnabled(HudConfig c, boolean on) {
        switch (this) {
            case ART -> c.art.enabled = on;
            case ICON -> c.icon.enabled = on;
            case TITLE -> c.title.enabled = on;
            case ARTIST -> c.artist.enabled = on;
            case ALBUM -> c.album.enabled = on;
            case TIME -> c.time.enabled = on;
            case BAR -> c.bar.enabled = on;
            case LYRICS -> c.lyrics.enabled = on;
            case PANEL -> {
            }
        }
    }

    /** {x, y, w, h} in the panel's own coordinates, or null when the element isn't laid out. */
    public int[] rect(HudLayout l) {
        return switch (this) {
            case ART -> l.art ? new int[] {l.artX, l.artY, l.artSize, l.artSize} : null;
            case ICON -> l.icon ? new int[] {l.iconX, l.iconY, l.iconSize, l.iconSize} : null;
            case TITLE -> line(l.title);
            case ARTIST -> line(l.artist);
            case ALBUM -> line(l.album);
            case TIME -> line(l.time);
            // the bar is thin: give it a few pixels more to click
            case BAR -> l.bar ? new int[] {l.barX, l.barY - 2, l.barWidth, l.barHeight + 4} : null;
            case LYRICS -> l.lyricsRows > 0
                    ? new int[] {l.lyricsX, l.lyricsY, l.lyricsWidth, l.lyricsRows * l.lyricsRowHeight} : null;
            case PANEL -> new int[] {0, 0, l.width, l.height};
        };
    }

    private static int[] line(HudLayout.Line line) {
        return line.visible && line.width > 0 ? new int[] {line.x, line.y, line.width, line.height} : null;
    }

    private static String percent(double v) {
        return Math.round(v * 100) + " %";
    }

    private static String px(double v) {
        return Math.round(v) + " px";
    }

    /** The settings shown in the panel for this element. */
    public List<Setting> settings(HudConfig c) {
        List<Setting> out = new ArrayList<>();
        if (this != PANEL) {
            out.add(new Setting.Toggle(t("elements.text.enabled"), () -> enabled(c), on -> setEnabled(c, on)));
        }
        switch (this) {
            case ART -> {
                out.add(intRange("elements.art.size", 16, 128, 2, () -> c.art.size, v -> c.art.size = v, HudElement::px));
                out.add(intRange("elements.art.rounding", 0, 50, 1, () -> c.art.rounding, v -> c.art.rounding = v,
                        v -> Math.round(v) + " %"));
                out.add(new Setting.Toggle(t("elements.art.shadow"), () -> c.art.shadow, v -> c.art.shadow = v));
            }
            case ICON -> out.add(intRange("elements.icon.size", 6, 32, 1, () -> c.icon.size, v -> c.icon.size = v,
                    HudElement::px));
            case TITLE -> out.add(scale(() -> c.title.scale, v -> c.title.scale = v));
            case ARTIST -> out.add(scale(() -> c.artist.scale, v -> c.artist.scale = v));
            case ALBUM -> out.add(scale(() -> c.album.scale, v -> c.album.scale = v));
            case TIME -> {
                out.add(scale(() -> c.time.scale, v -> c.time.scale = v));
                out.add(choice("elements.time.format", HudConfig.TimeFormat.class, () -> c.time.format,
                        v -> c.time.format = v));
            }
            case BAR -> {
                out.add(intRange("elements.bar.width", 0, 400, 10, () -> c.bar.width, v -> c.bar.width = v,
                        v -> v == 0 ? t("elements.bar.width.auto").getString() : px(v)));
                out.add(intRange("elements.bar.height", 1, 8, 1, () -> c.bar.height, v -> c.bar.height = v,
                        HudElement::px));
            }
            case LYRICS -> {
                out.add(intRange("elements.lyrics.lines_before", 0, 3, 1, () -> c.lyrics.linesBefore,
                        v -> c.lyrics.linesBefore = v, v -> String.valueOf(Math.round(v))));
                out.add(intRange("elements.lyrics.lines_after", 0, 3, 1, () -> c.lyrics.linesAfter,
                        v -> c.lyrics.linesAfter = v, v -> String.valueOf(Math.round(v))));
                out.add(scale(() -> c.lyrics.scale, v -> c.lyrics.scale = v));
                out.add(new Setting.Toggle(t("elements.lyrics.highlight_words"), () -> c.lyrics.highlightWords,
                        v -> c.lyrics.highlightWords = v));
                out.add(new Setting.Toggle(t("elements.lyrics.smooth"), () -> c.lyrics.smooth,
                        v -> c.lyrics.smooth = v));
                out.add(intRange("elements.lyrics.width", 0, 400, 10, () -> c.lyrics.width, v -> c.lyrics.width = v,
                        v -> v == 0 ? t("elements.bar.width.auto").getString() : px(v)));
            }
            case PANEL -> {
                out.add(choice("hud.orientation", HudConfig.Orientation.class, () -> c.orientation,
                        v -> c.orientation = v));
                out.add(choice("hud.text_align", HudConfig.Align.class, () -> c.textAlign, v -> c.textAlign = v));
                out.add(intRange("hud.padding", 0, 16, 1, () -> c.padding, v -> c.padding = v, HudElement::px));
                out.add(intRange("hud.gap", 0, 16, 1, () -> c.gap, v -> c.gap = v, HudElement::px));
                out.add(intRange("hud.max_text_width", 40, 400, 10, () -> c.maxTextWidth, v -> c.maxTextWidth = v,
                        HudElement::px));
                out.add(intRange("hud.marquee_speed", 0, 100, 5, () -> c.marqueeSpeed, v -> c.marqueeSpeed = v,
                        v -> v == 0 ? t("off").getString() : Math.round(v) + " px/s"));
                out.add(choice("hud.background", HudConfig.Background.class, () -> c.background,
                        v -> c.background = v));
                out.add(intRange("hud.background_opacity", 0, 100, 5, () -> Math.round((c.backgroundColor >>> 24) / 2.55f),
                        v -> c.backgroundColor = (Math.round(v * 2.55f) << 24) | (c.backgroundColor & 0xFFFFFF),
                        v -> Math.round(v) + " %"));
                out.add(new Setting.Toggle(t("hud.border"), () -> c.border, v -> c.border = v));
                out.add(choice("hud.connection_status", SonocraftConfig.StatusVisibility.class,
                        () -> c.connectionStatus, v -> c.connectionStatus = v));
            }
        }
        return out;
    }

    private static Setting intRange(String key, int min, int max, int step, Supplier<Integer> get,
                                    Consumer<Integer> set, java.util.function.DoubleFunction<String> format) {
        return new Setting.Range(t(key), min, max, step, () -> (double) get.get(), v -> set.accept((int) Math.round(v)),
                format);
    }

    private static Setting scale(Supplier<Float> get, Consumer<Float> set) {
        return new Setting.Range(t("elements.text.scale"), 0.5, 2, 0.05, () -> (double) get.get(),
                v -> set.accept((float) (double) v), HudElement::percent);
    }

    private static <E extends Enum<E>> Setting choice(String key, Class<E> type, Supplier<E> get, Consumer<E> set) {
        return new Setting.Choice<>(t(key), type, get, set, "sonocraft.config." + key + ".");
    }

    String chipKey() {
        return "sonocraft.config." + key;
    }

    static String name(HudElement e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
