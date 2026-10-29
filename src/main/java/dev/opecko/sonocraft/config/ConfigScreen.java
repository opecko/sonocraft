package dev.opecko.sonocraft.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.hud.HudEditorScreen;
import dev.opecko.sonocraft.theme.ThemeManager;
import dev.opecko.sonocraft.ui.ConnectionScreen;
import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The YACL config screen. Built by hand (not from annotations) to control groups and buttons. OneConfig picks it
 * up through its YACL compat and the ModMenu entrypoint (PLAN §9).
 */
public final class ConfigScreen {
    private ConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SonocraftConfig c = SonocraftCore.get().config.get();
        return YetAnotherConfigLib.createBuilder()
                .title(t("title"))
                .category(ConfigCategory.createBuilder()
                        .name(t("connection"))
                        .option(LabelOption.create(Component.translatable("sonocraft.config.connection.status",
                                Texts.status(SonocraftCore.get().session.status()))))
                        .option(ButtonOption.createBuilder()
                                .name(t("connection.manage"))
                                .description(OptionDescription.of(t("connection.manage.description")))
                                .action((screen, option) -> Mc.setScreen(new ConnectionScreen(currentScreen(screen))))
                                .build())
                        .option(bool("connection.toasts", true, () -> c.connection.toasts, v -> c.connection.toasts = v))
                        .group(OptionGroup.createBuilder()
                                .name(t("connection.credit"))
                                .option(LabelOption.create(t("connection.credit.text")))
                                .option(ButtonOption.createBuilder()
                                        .name(t("connection.credit.open"))
                                        .action((screen, option) -> ConfirmLinkScreen.confirmLinkNow(currentScreen(screen),
                                                ConnectionScreen.SONOLITH))
                                        .build())
                                .build())
                        .build())
                .category(hudCategory(c.hud, parent))
                .category(elementsCategory(c.hud, parent))
                .category(ConfigCategory.createBuilder()
                        .name(t("theme"))
                        .option(Option.<ThemeManager.Mode>createBuilder()
                                .name(t("theme.mode"))
                                .description(OptionDescription.of(t("theme.mode.description")))
                                .binding(ThemeManager.Mode.ART_OR_MANUAL, () -> c.theme.mode, v -> c.theme.mode = v)
                                .controller(o -> EnumControllerBuilder.create(o)
                                        .enumClass(ThemeManager.Mode.class)
                                        .formatValue(v -> t("theme.mode." + v.name().toLowerCase(Locale.ROOT))))
                                .build())
                        .option(Option.<Color>createBuilder()
                                .name(t("theme.manual_color"))
                                .description(OptionDescription.of(t("theme.manual_color.description")))
                                .binding(new Color(0xE94560), () -> new Color(c.theme.manualColor),
                                        v -> c.theme.manualColor = v.getRGB() & 0xFFFFFF)
                                .controller(o -> ColorControllerBuilder.create(o).allowAlpha(false))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(t("theme.transition"))
                                .description(OptionDescription.of(t("theme.transition.description")))
                                .binding(400, () -> c.theme.transitionMs, v -> c.theme.transitionMs = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0, 2000).step(50)
                                        .formatValue(v -> Component.literal(v + " ms")))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(t("library"))
                        .option(bool("library.pause_game", false, () -> c.library.pauseGame, v -> c.library.pauseGame = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(t("controls"))
                        .option(bool("controls.feedback", true, () -> c.controls.feedback, v -> c.controls.feedback = v))
                        .option(LabelOption.create(t("controls.keys")))
                        .build())
                .save(() -> {
                    SonocraftCore.get().config.save();
                    SonocraftCore.get().applyConfig();
                })
                .build()
                .generateScreen(parent);
    }

    /**
     * The screen to return to from a button. YACL passes its own screen, but OneConfig shows our options in its
     * own UI, so the screen that is actually open is the right parent.
     */
    private static Screen currentScreen(Screen yaclScreen) {
        Screen open = Mc.screen();
        return open != null ? open : yaclScreen;
    }

    private static ConfigCategory hudCategory(HudConfig h, Screen parent) {
        HudConfig d = new HudConfig();
        return ConfigCategory.createBuilder()
                .name(t("hud"))
                .option(bool("hud.enabled", d.enabled, () -> h.enabled, v -> h.enabled = v))
                .option(editorButton(parent))
                .option(enumOption("hud.connection_status", SonocraftConfig.StatusVisibility.class, d.connectionStatus,
                        () -> h.connectionStatus, v -> h.connectionStatus = v))
                .group(OptionGroup.createBuilder()
                        .name(t("hud.layout"))
                        .option(enumOption("hud.orientation", HudConfig.Orientation.class, d.orientation,
                                () -> h.orientation, v -> h.orientation = v))
                        .option(enumOption("hud.text_align", HudConfig.Align.class, d.textAlign,
                                () -> h.textAlign, v -> h.textAlign = v))
                        .option(intSlider("hud.padding", 0, 16, 1, d.padding, () -> h.padding, v -> h.padding = v, PX))
                        .option(intSlider("hud.gap", 0, 16, 1, d.gap, () -> h.gap, v -> h.gap = v, PX))
                        .option(intSlider("hud.max_text_width", 40, 400, 10, d.maxTextWidth, () -> h.maxTextWidth,
                                v -> h.maxTextWidth = v, PX))
                        .option(intSlider("hud.marquee_speed", 0, 100, 5, d.marqueeSpeed, () -> h.marqueeSpeed,
                                v -> h.marqueeSpeed = v, v -> v == 0 ? t("off") : Component.literal(v + " px/s")))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(t("hud.background_group"))
                        .option(enumOption("hud.background", HudConfig.Background.class, d.background,
                                () -> h.background, v -> h.background = v))
                        .option(Option.<Color>createBuilder()
                                .name(t("hud.background_color"))
                                .description(OptionDescription.of(t("hud.background_color.description")))
                                .binding(new Color(d.backgroundColor, true), () -> new Color(h.backgroundColor, true),
                                        v -> h.backgroundColor = v.getRGB())
                                .controller(o -> ColorControllerBuilder.create(o).allowAlpha(true))
                                .build())
                        .option(bool("hud.border", d.border, () -> h.border, v -> h.border = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(t("hud.visibility"))
                        .option(bool("hud.hide_nothing_playing", d.hideWhenNothingPlaying,
                                () -> h.hideWhenNothingPlaying, v -> h.hideWhenNothingPlaying = v))
                        .option(intSlider("hud.hide_after_paused", 0, 600, 10, d.hideAfterPausedSeconds,
                                () -> h.hideAfterPausedSeconds, v -> h.hideAfterPausedSeconds = v,
                                v -> v == 0 ? t("never") : Component.literal(v + " s")))
                        .option(bool("hud.hide_in_screens", d.hideInScreens, () -> h.hideInScreens,
                                v -> h.hideInScreens = v))
                        .build())
                .build();
    }

    /** "Open HUD editor…", in both HUD categories. */
    private static ButtonOption editorButton(Screen parent) {
        return ButtonOption.createBuilder()
                .name(t("hud.editor"))
                .description(OptionDescription.of(t("hud.editor.description")))
                .action((screen, option) -> {
                    Screen open = Mc.screen();
                    // in our YACL screen: rebuild it on return, the editor changes values its options hold;
                    // inside OneConfig's UI: go back to OneConfig, not to a YACL screen it never showed
                    Supplier<Screen> back = open == null || open == screen ? () -> create(parent) : () -> open;
                    Mc.setScreen(new HudEditorScreen(back));
                })
                .build();
    }

    private static ConfigCategory elementsCategory(HudConfig h, Screen parent) {
        HudConfig d = new HudConfig();
        return ConfigCategory.createBuilder()
                .name(t("elements"))
                .option(editorButton(parent))
                .group(OptionGroup.createBuilder()
                        .collapsed(true)
                        .name(t("elements.art"))
                        .option(bool("elements.art.enabled", d.art.enabled, () -> h.art.enabled, v -> h.art.enabled = v))
                        .option(intSlider("elements.art.size", 16, 128, 2, d.art.size, () -> h.art.size,
                                v -> h.art.size = v, PX))
                        .option(intSlider("elements.art.rounding", 0, 50, 1, d.art.rounding, () -> h.art.rounding,
                                v -> h.art.rounding = v, v -> Component.literal(v + " %")))
                        .option(bool("elements.art.shadow", d.art.shadow, () -> h.art.shadow, v -> h.art.shadow = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .collapsed(true)
                        .name(t("elements.icon"))
                        .option(bool("elements.icon.enabled", d.icon.enabled, () -> h.icon.enabled, v -> h.icon.enabled = v))
                        .option(intSlider("elements.icon.size", 6, 32, 1, d.icon.size, () -> h.icon.size,
                                v -> h.icon.size = v, PX))
                        .build())
                .group(textGroup("title", h.title))
                .group(textGroup("artist", h.artist))
                .group(textGroup("album", h.album))
                .group(OptionGroup.createBuilder()
                        .collapsed(true)
                        .name(t("elements.time"))
                        .option(bool("elements.time.enabled", d.time.enabled, () -> h.time.enabled, v -> h.time.enabled = v))
                        .option(floatSlider("elements.time.scale", d.time.scale, () -> h.time.scale, v -> h.time.scale = v))
                        .option(enumOption("elements.time.format", HudConfig.TimeFormat.class, d.time.format,
                                () -> h.time.format, v -> h.time.format = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .collapsed(true)
                        .name(t("elements.lyrics"))
                        .option(bool("elements.lyrics.enabled", d.lyrics.enabled, () -> h.lyrics.enabled,
                                v -> h.lyrics.enabled = v))
                        .option(intSlider("elements.lyrics.lines_before", 0, 3, 1, d.lyrics.linesBefore,
                                () -> h.lyrics.linesBefore, v -> h.lyrics.linesBefore = v, v -> Component.literal(v.toString())))
                        .option(intSlider("elements.lyrics.lines_after", 0, 3, 1, d.lyrics.linesAfter,
                                () -> h.lyrics.linesAfter, v -> h.lyrics.linesAfter = v, v -> Component.literal(v.toString())))
                        .option(floatSlider("elements.lyrics.scale", d.lyrics.scale, () -> h.lyrics.scale,
                                v -> h.lyrics.scale = v))
                        .option(bool("elements.lyrics.highlight_words", d.lyrics.highlightWords,
                                () -> h.lyrics.highlightWords, v -> h.lyrics.highlightWords = v))
                        .option(bool("elements.lyrics.smooth", d.lyrics.smooth, () -> h.lyrics.smooth,
                                v -> h.lyrics.smooth = v))
                        .option(intSlider("elements.lyrics.width", 0, 400, 10, d.lyrics.width, () -> h.lyrics.width,
                                v -> h.lyrics.width = v, v -> v == 0 ? t("elements.bar.width.auto") : Component.literal(v + " px")))
                        .build())
                .group(OptionGroup.createBuilder()
                        .collapsed(true)
                        .name(t("elements.bar"))
                        .option(bool("elements.bar.enabled", d.bar.enabled, () -> h.bar.enabled, v -> h.bar.enabled = v))
                        .option(intSlider("elements.bar.width", 0, 400, 10, d.bar.width, () -> h.bar.width,
                                v -> h.bar.width = v, v -> v == 0 ? t("elements.bar.width.auto") : Component.literal(v + " px")))
                        .option(intSlider("elements.bar.height", 1, 8, 1, d.bar.height, () -> h.bar.height,
                                v -> h.bar.height = v, PX))
                        .build())
                .build();
    }

    private static OptionGroup textGroup(String name, HudConfig.Text text) {
        return OptionGroup.createBuilder()
                .collapsed(true)
                .name(t("elements." + name))
                .option(bool("elements.text.enabled", true, () -> text.enabled, v -> text.enabled = v))
                .option(floatSlider("elements.text.scale", 1f, () -> text.scale, v -> text.scale = v))
                .build();
    }

    private static final Function<Integer, Component> PX = v -> Component.literal(v + " px");

    private static <E extends Enum<E>> Option<E> enumOption(String key, Class<E> type, E def, Supplier<E> get,
                                                            Consumer<E> set) {
        return Option.<E>createBuilder()
                .name(t(key))
                .description(OptionDescription.of(t(key + ".description")))
                .binding(def, get, set)
                .controller(o -> EnumControllerBuilder.create(o)
                        .enumClass(type)
                        .formatValue(v -> t(key + "." + v.name().toLowerCase(Locale.ROOT))))
                .build();
    }

    private static Option<Integer> intSlider(String key, int min, int max, int step, int def, Supplier<Integer> get,
                                             Consumer<Integer> set, Function<Integer, Component> format) {
        return Option.<Integer>createBuilder()
                .name(t(key))
                .description(OptionDescription.of(t(key + ".description")))
                .binding(def, get, set)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(step)
                        .formatValue(format::apply))
                .build();
    }

    private static Option<Float> floatSlider(String key, float def, Supplier<Float> get, Consumer<Float> set) {
        return Option.<Float>createBuilder()
                .name(t(key))
                .description(OptionDescription.of(t(key + ".description")))
                .binding(def, get, set)
                .controller(o -> FloatSliderControllerBuilder.create(o).range(0.5f, 2f).step(0.05f)
                        .formatValue(v -> Component.literal(Math.round(v * 100) + " %")))
                .build();
    }

    private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(t(key))
                .description(OptionDescription.of(t(key + ".description")))
                .binding(def, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Component t(String key) {
        return Component.translatable("sonocraft.config." + key);
    }
}
