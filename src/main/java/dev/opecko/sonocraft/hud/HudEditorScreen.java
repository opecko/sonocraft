package dev.opecko.sonocraft.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.hud.editor.HudElement;
import dev.opecko.sonocraft.hud.editor.SettingsPanel;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Edits the HUD on screen (PLAN §5.2): drag the panel to move it (it snaps to the screen edges and centre), scroll
 * to scale it, click a part of it (artwork, title, time, lyrics…) to show that part's settings beside it, or the
 * panel's background for the panel's own. The chips at the top switch parts on and off. Shows sample texts when
 * nothing is playing. Changes apply live and are saved when the screen closes.
 */
public final class HudEditorScreen extends Screen {
    private static final int MARGIN = 4;
    private static final int SNAP = 6;
    private static final int GUIDE = 0x60FFFFFF;

    /** Moving the mouse this far with the button down turns a click into a drag. */
    private static final int DRAG_START = 3;
    private static final int CHIPS_Y = 4;
    /** Parts checked for a click, front ones first; PANEL when none matches. */
    private static final HudElement[] HIT_ORDER = {
        HudElement.ICON, HudElement.TITLE, HudElement.ARTIST, HudElement.ALBUM, HudElement.TIME, HudElement.BAR,
        HudElement.ART, HudElement.LYRICS};

    private final Supplier<Screen> back;
    private final SettingsPanel panel = new SettingsPanel();
    private boolean pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private float grabX;
    private float grabY;
    private boolean snappedX;
    private boolean snappedY;

    /** @param back the screen to return to (built when closing, so a config screen shows fresh values) */
    public HudEditorScreen(Supplier<Screen> back) {
        super(Component.translatable("sonocraft.editor.title"));
        this.back = back;
    }

    private static HudConfig config() {
        return SonocraftCore.get().config.get().hud;
    }

    private static HudLayout layout() {
        return SonocraftCore.get().hud.layout();
    }

    private static int accent() {
        return SonocraftCore.get().theme.palette(System.currentTimeMillis()).accent();
    }

    private void select(HudElement element) {
        panel.show(element, element.settings(config()));
    }

    /** The part of the HUD under the mouse, PANEL for its background, or null outside it. */
    private HudElement elementAt(double mx, double my) {
        if (!inside(mx, my)) {
            return null;
        }
        HudLayout l = layout();
        double lx = (mx - l.screenX) / l.scale;
        double ly = (my - l.screenY) / l.scale;
        for (HudElement e : HIT_ORDER) {
            int[] r = e.rect(l);
            if (r != null && lx >= r[0] && ly >= r[1] && lx < r[0] + r[2] && ly < r[1] + r[3]) {
                return e;
            }
        }
        return HudElement.PANEL;
    }

    /** Outlines a part of the HUD in screen coordinates. */
    private static void outline(Gfx g, HudElement e, int color) {
        HudLayout l = layout();
        int[] r = e.rect(l);
        if (r == null) {
            return;
        }
        int x1 = Math.round(l.screenX + r[0] * l.scale) - 1;
        int y1 = Math.round(l.screenY + r[1] * l.scale) - 1;
        int x2 = Math.round(l.screenX + (r[0] + r[2]) * l.scale) + 1;
        int y2 = Math.round(l.screenY + (r[1] + r[3]) * l.scale) + 1;
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    // chips at the top: one per part, filled when the part is on
    private final List<int[]> chips = new ArrayList<>();
    private int chipsY = CHIPS_Y;

    private void drawChips(Gfx g, int mouseX, int mouseY) {
        chips.clear();
        HudElement[] all = HudElement.values();
        int total = 0;
        for (HudElement e : all) {
            total += g.textWidth(e.label()) + 10 + 3;
        }
        int x = Math.max(4, (width - total) / 2);
        for (HudElement e : all) {
            int w = g.textWidth(e.label()) + 10;
            boolean on = e.enabled(config());
            boolean selected = panel.element() == e;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= chipsY && mouseY < chipsY + 14;
            int bg = on ? (selected ? accent() : 0x60000000) : 0x30000000;
            g.fill(x, chipsY, x + w, chipsY + 14, bg);
            if (hover) {
                g.fill(x, chipsY, x + w, chipsY + 14, 0x30FFFFFF);
            }
            if (selected && !on) {
                g.fill(x, chipsY + 13, x + w, chipsY + 14, accent());
            }
            g.text(e.label(), x + 5, chipsY + 3, on ? UiColors.TEXT : UiColors.TEXT_FAINT, false);
            chips.add(new int[] {x, w, e.ordinal()});
            x += w + 3;
        }
    }

    /** A click on a chip selects the part; a second click on a selected part switches it on or off. */
    private boolean clickChip(double mx, double my) {
        if (my < chipsY || my >= chipsY + 14) {
            return false;
        }
        for (int[] c : chips) {
            if (mx >= c[0] && mx < c[0] + c[1]) {
                HudElement e = HudElement.values()[c[2]];
                if (panel.element() == e && e != HudElement.PANEL) {
                    e.setEnabled(config(), !e.enabled(config()));
                } else {
                    select(e);
                    if (!e.enabled(config())) {
                        e.setEnabled(config(), true);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void init() {
        int y = height - 28;
        // as wide as the longest label, so translations and large GUI scales still fit
        int w = font.width(Component.translatable("sonocraft.editor.reset"));
        for (HudConfig.Preset preset : HudConfig.Preset.values()) {
            w = Math.max(w, font.width(Component.translatable(
                    "sonocraft.editor.preset." + preset.name().toLowerCase(java.util.Locale.ROOT))));
        }
        w = Math.clamp(w + 12, 70, Math.max(70, (width - 8 - 4 * 4) / 5));
        int x = width / 2 - (5 * w + 4 * 4) / 2;
        for (HudConfig.Preset preset : HudConfig.Preset.values()) {
            String key = "sonocraft.editor.preset." + preset.name().toLowerCase(java.util.Locale.ROOT);
            addRenderableWidget(Button.builder(Component.translatable(key), b -> config().apply(preset))
                    .bounds(x, y, w, 20).build());
            x += w + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("sonocraft.editor.reset"), b -> {
            HudConfig c = config();
            HudConfig d = new HudConfig();
            c.anchor = d.anchor;
            c.offsetX = d.offsetX;
            c.offsetY = d.offsetY;
            c.scale = d.scale;
        }).bounds(x, y, w, 20).build());
        x += w + 4;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y, w, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        drawEditor(new Gfx(g), mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) {
            super.extractBackground(g, mouseX, mouseY, delta);
        } else {
            g.fill(0, 0, width, height, 0x30000000); // keep the world visible to place the HUD against it
        }
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        drawEditor(new Gfx(g), mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) {
            super.renderBackground(g, mouseX, mouseY, delta);
        } else {
            g.fill(0, 0, width, height, 0x30000000);
        }
    }
    *///?}

    private void drawEditor(Gfx g, int mouseX, int mouseY) {
        if (dragging && snappedX) {
            g.fill(width / 2, 0, width / 2 + 1, height, GUIDE);
        }
        if (dragging && snappedY) {
            g.fill(0, height / 2, width, height / 2 + 1, GUIDE);
        }
        SonocraftCore.get().hud.renderPreview(g);
        HudLayout l = layout();
        long now = System.currentTimeMillis();
        boolean overPanel = panel.contains(mouseX, mouseY);
        outline(g, HudElement.PANEL, dragging || (!overPanel && inside(mouseX, mouseY)) ? 0xFFFFFFFF : 0x80FFFFFF);
        HudElement hovered = dragging || overPanel ? null : elementAt(mouseX, mouseY);
        if (hovered != null && hovered != HudElement.PANEL && hovered != panel.element()) {
            outline(g, hovered, 0x80FFFFFF);
        }
        if (panel.element() != null && panel.element() != HudElement.PANEL) {
            outline(g, panel.element(), accent());
        }
        // the chips move down above the hint when the HUD sits at the top edge, so they never cover it
        boolean chipsTop = l.screenY >= CHIPS_Y + 16;
        chipsY = chipsTop ? CHIPS_Y : height - 62;
        drawChips(g, mouseX, mouseY);
        // the settings sit on the other side of the screen from the HUD, so they never cover it
        boolean hudLeft = l.screenX + l.width * l.scale / 2 < width / 2f;
        panel.place(hudLeft ? width - SettingsPanel.WIDTH - 6 : 6, chipsTop ? CHIPS_Y + 20 : 6,
                chipsTop ? height - 50 : chipsY - 6);
        panel.render(g, mouseX, mouseY, now, HudEditorScreen::accent);
        g.centeredText(Component.translatable("sonocraft.editor.hint", Math.round(config().scale * 100)),
                width / 2, height - 42, 0xFFFFFFFF);
    }

    private boolean inside(double mx, double my) {
        HudLayout l = layout();
        return mx >= l.screenX && my >= l.screenY && mx < l.screenX + l.width * l.scale
                && my < l.screenY + l.height * l.scale;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (panel.mouseClicked(event.x(), event.y(), event.button()) || clickChip(event.x(), event.y())) {
            return true;
        }
        // not a literal 0: 26.3 uses SDL button numbers, where the left button is 1
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())) {
            pressed = true;
            pressX = event.x();
            pressY = event.y();
            grabX = (float) event.x() - layout().screenX;
            grabY = (float) event.y() - layout().screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (panel.mouseDragged(event.x(), event.y(), event.button())) {
            return true;
        }
        if (pressed && !dragging && Math.hypot(event.x() - pressX, event.y() - pressY) >= DRAG_START) {
            dragging = true;
        }
        if (!dragging) {
            return super.mouseDragged(event, dx, dy);
        }
        HudLayout l = layout();
        float w = l.width * l.scale;
        float h = l.height * l.scale;
        float x = (float) event.x() - grabX;
        float y = (float) event.y() - grabY;
        float sx = snap(x, MARGIN, width - w - MARGIN, (width - w) / 2);
        float sy = snap(y, MARGIN, height - h - MARGIN, (height - h) / 2);
        snappedX = sx == (width - w) / 2;
        snappedY = sy == (height - h) / 2;
        HudLayout.storePosition(config(), sx, sy, w, h, width, height);
        return true;
    }

    private static float snap(float v, float start, float end, float centre) {
        for (float target : new float[] {start, end, centre}) {
            if (Math.abs(v - target) < SNAP) {
                return target;
            }
        }
        return v;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (panel.mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        if (pressed) {
            pressed = false;
            if (dragging) {
                dragging = false;
            } else {
                // a click without moving selects the part under the mouse
                HudElement e = elementAt(event.x(), event.y());
                if (e != null) {
                    select(e);
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        if (panel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        HudConfig c = config();
        HudLayout l = layout();
        float cx = l.screenX + l.width * l.scale / 2;
        float cy = l.screenY + l.height * l.scale / 2;
        float scale = Math.round((c.scale + (scrollY > 0 ? 0.05f : -0.05f)) * 20) / 20f;
        c.scale = Math.max(0.5f, Math.min(3f, scale));
        // keep the panel's centre where it was
        float w = l.width * c.scale;
        float h = l.height * c.scale;
        HudLayout.storePosition(c, cx - w / 2, cy - h / 2, w, h, width, height);
        return true;
    }

    @Override
    public void onClose() {
        SonocraftCore core = SonocraftCore.get();
        core.config.save();
        core.applyConfig();
        Mc.setScreen(back.get());
    }
}
