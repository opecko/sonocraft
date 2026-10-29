package dev.opecko.sonocraft.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.config.HudConfig;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Moves and resizes the HUD (PLAN §5.2): drag the panel, scroll to scale, snaps to the screen edges and centre.
 * Shows sample texts when nothing is playing. Changes apply live and are saved when the screen closes.
 */
public final class HudEditorScreen extends Screen {
    private static final int MARGIN = 4;
    private static final int SNAP = 6;
    private static final int GUIDE = 0x60FFFFFF;

    private final Supplier<Screen> back;
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

    @Override
    protected void init() {
        int y = height - 28;
        int w = 70;
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
        int x1 = Math.round(l.screenX) - 1;
        int y1 = Math.round(l.screenY) - 1;
        int x2 = Math.round(l.screenX + l.width * l.scale) + 1;
        int y2 = Math.round(l.screenY + l.height * l.scale) + 1;
        int outline = dragging || inside(mouseX, mouseY) ? 0xFFFFFFFF : 0x80FFFFFF;
        g.fill(x1, y1, x2, y1 + 1, outline);
        g.fill(x1, y2 - 1, x2, y2, outline);
        g.fill(x1, y1, x1 + 1, y2, outline);
        g.fill(x2 - 1, y1, x2, y2, outline);
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
        // not a literal 0: 26.3 uses SDL button numbers, where the left button is 1
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())) {
            dragging = true;
            grabX = (float) event.x() - layout().screenX;
            grabY = (float) event.y() - layout().screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
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
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
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
