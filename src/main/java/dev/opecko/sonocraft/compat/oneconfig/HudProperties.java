package dev.opecko.sonocraft.compat.oneconfig;

import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.config.SonocraftConfig;
import java.awt.Color;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;
import org.polyfrost.oneconfig.api.config.v1.dsl.Tree_extensionsKt;

/**
 * The HUD settings as OneConfig properties, shown in the side panel of OneConfig's HUD editor. They read and write
 * the live {@link HudConfig}, so YACL, our HUD editor and OneConfig always show the same values. Position and scale
 * are not here: OneConfig edits those itself. Labels reuse the YACL screen's translation keys.
 */
final class HudProperties {
    private final List<Property<?>> out = new ArrayList<>();
    private String category;

    private HudProperties() {
    }

    static List<Property<?>> create() {
        HudConfig d = new HudConfig();
        HudProperties p = new HudProperties();

        p.category = "hud";
        p.bool("hud.enabled", d.enabled, () -> h().enabled, v -> h().enabled = v);
        p.choice("hud.connection_status", SonocraftConfig.StatusVisibility.class, d.connectionStatus,
                () -> h().connectionStatus, v -> h().connectionStatus = v);

        p.category = "hud.layout";
        p.choice("hud.orientation", HudConfig.Orientation.class, d.orientation, () -> h().orientation,
                v -> h().orientation = v);
        p.choice("hud.text_align", HudConfig.Align.class, d.textAlign, () -> h().textAlign, v -> h().textAlign = v);
        p.slider("hud.padding", 0, 16, 1, d.padding, () -> h().padding, v -> h().padding = v);
        p.slider("hud.gap", 0, 16, 1, d.gap, () -> h().gap, v -> h().gap = v);
        p.slider("hud.max_text_width", 40, 400, 10, d.maxTextWidth, () -> h().maxTextWidth, v -> h().maxTextWidth = v);
        p.slider("hud.marquee_speed", 0, 100, 5, d.marqueeSpeed, () -> h().marqueeSpeed, v -> h().marqueeSpeed = v);

        p.category = "hud.background_group";
        p.choice("hud.background", HudConfig.Background.class, d.background, () -> h().background,
                v -> h().background = v);
        p.color("hud.background_color", d.backgroundColor, () -> h().backgroundColor, v -> h().backgroundColor = v);
        p.bool("hud.border", d.border, () -> h().border, v -> h().border = v);

        p.category = "hud.visibility";
        p.bool("hud.hide_nothing_playing", d.hideWhenNothingPlaying, () -> h().hideWhenNothingPlaying,
                v -> h().hideWhenNothingPlaying = v);
        p.slider("hud.hide_after_paused", 0, 600, 10, d.hideAfterPausedSeconds, () -> h().hideAfterPausedSeconds,
                v -> h().hideAfterPausedSeconds = v);
        p.bool("hud.hide_in_screens", d.hideInScreens, () -> h().hideInScreens, v -> h().hideInScreens = v);

        p.category = "elements.art";
        p.bool("elements.art.enabled", d.art.enabled, () -> h().art.enabled, v -> h().art.enabled = v);
        p.slider("elements.art.size", 16, 128, 2, d.art.size, () -> h().art.size, v -> h().art.size = v);
        p.slider("elements.art.rounding", 0, 50, 1, d.art.rounding, () -> h().art.rounding, v -> h().art.rounding = v);
        p.bool("elements.art.shadow", d.art.shadow, () -> h().art.shadow, v -> h().art.shadow = v);

        p.category = "elements.icon";
        p.bool("elements.icon.enabled", d.icon.enabled, () -> h().icon.enabled, v -> h().icon.enabled = v);
        p.slider("elements.icon.size", 6, 32, 1, d.icon.size, () -> h().icon.size, v -> h().icon.size = v);

        p.text("title", () -> h().title);
        p.text("artist", () -> h().artist);
        p.text("album", () -> h().album);

        p.category = "elements.time";
        p.bool("elements.time.enabled", d.time.enabled, () -> h().time.enabled, v -> h().time.enabled = v);
        p.scale("elements.time.scale", d.time.scale, () -> h().time.scale, v -> h().time.scale = v);
        p.choice("elements.time.format", HudConfig.TimeFormat.class, d.time.format, () -> h().time.format,
                v -> h().time.format = v);

        p.category = "elements.lyrics";
        p.bool("elements.lyrics.enabled", d.lyrics.enabled, () -> h().lyrics.enabled, v -> h().lyrics.enabled = v);
        p.slider("elements.lyrics.lines_before", 0, 3, 1, d.lyrics.linesBefore, () -> h().lyrics.linesBefore,
                v -> h().lyrics.linesBefore = v);
        p.slider("elements.lyrics.lines_after", 0, 3, 1, d.lyrics.linesAfter, () -> h().lyrics.linesAfter,
                v -> h().lyrics.linesAfter = v);
        p.scale("elements.lyrics.scale", d.lyrics.scale, () -> h().lyrics.scale, v -> h().lyrics.scale = v);
        p.bool("elements.lyrics.highlight_words", d.lyrics.highlightWords, () -> h().lyrics.highlightWords,
                v -> h().lyrics.highlightWords = v);
        p.bool("elements.lyrics.smooth", d.lyrics.smooth, () -> h().lyrics.smooth, v -> h().lyrics.smooth = v);
        p.slider("elements.lyrics.width", 0, 400, 10, d.lyrics.width, () -> h().lyrics.width, v -> h().lyrics.width = v);

        p.category = "elements.bar";
        p.bool("elements.bar.enabled", d.bar.enabled, () -> h().bar.enabled, v -> h().bar.enabled = v);
        p.slider("elements.bar.width", 0, 400, 10, d.bar.width, () -> h().bar.width, v -> h().bar.width = v);
        p.slider("elements.bar.height", 1, 8, 1, d.bar.height, () -> h().bar.height, v -> h().bar.height = v);
        return p.out;
    }

    private static HudConfig h() {
        return SonocraftCore.get().config.get().hud;
    }

    /**
     * A translation key. OneConfig translates names, descriptions and categories itself when it draws them; we are
     * registered right after client start, before the language files are loaded, so resolving them here would only
     * give back the keys.
     */
    private static String key(String key) {
        return "sonocraft.config." + key;
    }

    /** Dropdown labels are shown as they are, so this list translates each one when OneConfig reads it. */
    private static List<String> lazyLabels(List<String> keys) {
        return new AbstractList<>() {
            @Override
            public String get(int index) {
                return Component.translatable(keys.get(index)).getString();
            }

            @Override
            public int size() {
                return keys.size();
            }
        };
    }

    /** Adds the property under the current category; the id must be unique in the HUD's tree. */
    private <T> Property<T> add(String key, Supplier<T> get, Consumer<T> set, T def, Class<T> type,
                                Class<? extends Visualizer> visualizer) {
        String id = "sonocraft_" + category.replace('.', '_') + "_" + key.substring(key.lastIndexOf('.') + 1);
        Property<T> prop = Properties.functional(get, set, id, key(key), key(key + ".description"), type);
        prop.addMetadata("visualizer", visualizer);
        prop.addMetadata("default", def);
        Tree_extensionsKt.setCategory(prop, key(category));
        out.add(prop);
        return prop;
    }

    private void bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        add(key, get, set, def, Boolean.class, Visualizer.SwitchVisualizer.class);
    }

    private void slider(String key, int min, int max, int step, int def, Supplier<Integer> get, Consumer<Integer> set) {
        Property<Integer> prop = add(key, get, set, def, Integer.class, Visualizer.SliderVisualizer.class);
        prop.addMetadata("min", (float) min);
        prop.addMetadata("max", (float) max);
        prop.addMetadata("step", (float) step);
    }

    private void scale(String key, float def, Supplier<Float> get, Consumer<Float> set) {
        Property<Float> prop = add(key, get, set, def, Float.class, Visualizer.SliderVisualizer.class);
        prop.addMetadata("min", 0.5f);
        prop.addMetadata("max", 2f);
        prop.addMetadata("step", 0.05f);
    }

    private <E extends Enum<E>> void choice(String key, Class<E> type, E def, Supplier<E> get, Consumer<E> set) {
        Property<E> prop = add(key, get, set, def, type, Visualizer.DropdownVisualizer.class);
        List<E> values = Arrays.asList(type.getEnumConstants());
        prop.addMetadata("options", values.stream().map(Enum::name).toList());
        prop.addMetadata("optionValues", values);
        prop.addMetadata("optionLabels", lazyLabels(
                values.stream().map(v -> key(key + "." + v.name().toLowerCase(Locale.ROOT))).toList()));
    }

    private void color(String key, int def, Supplier<Integer> get, Consumer<Integer> set) {
        add(key, () -> new Color(get.get(), true), v -> set.accept(v.getRGB()), new Color(def, true), Color.class,
                Visualizer.ColorVisualizer.class);
    }

    /** Show and size for a text line (title, artist, album), in its own category. */
    private void text(String name, Supplier<HudConfig.Text> line) {
        category = "elements." + name;
        add("elements.text.enabled", () -> line.get().enabled, v -> line.get().enabled = v, true, Boolean.class,
                Visualizer.SwitchVisualizer.class);
        scale("elements.text.scale", 1f, () -> line.get().scale, v -> line.get().scale = v);
    }
}
