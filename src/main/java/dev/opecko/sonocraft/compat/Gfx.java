package dev.opecko.sonocraft.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Version-independent HUD drawing: in 26.1+ it's {@code GuiGraphicsExtractor} (method {@code text}), in older
 * versions {@code GuiGraphics} ({@code drawString}). Colors are always ARGB (alpha 0 = invisible).
 */
public final class Gfx {
    //? if >=26.1 {
    private final net.minecraft.client.gui.GuiGraphicsExtractor g;

    public Gfx(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        this.g = g;
    }
    //?} else {
    /*private final net.minecraft.client.gui.GuiGraphics g;

    public Gfx(net.minecraft.client.gui.GuiGraphics g) {
        this.g = g;
    }
    *///?}

    public int width() {
        return g.guiWidth();
    }

    public int height() {
        return g.guiHeight();
    }

    public Font font() {
        return Minecraft.getInstance().font;
    }

    public int textWidth(String s) {
        return font().width(s);
    }

    public int textWidth(Component c) {
        return font().width(c);
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    public void text(String s, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        g.text(font(), s, x, y, argb, shadow);
        //?} else {
        /*g.drawString(font(), s, x, y, argb, shadow);
        *///?}
    }

    public void text(Component c, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        g.text(font(), c, x, y, argb, shadow);
        //?} else {
        /*g.drawString(font(), c, x, y, argb, shadow);
        *///?}
    }

    public void text(FormattedCharSequence line, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        g.text(font(), line, x, y, argb, shadow);
        //?} else {
        /*g.drawString(font(), line, x, y, argb, shadow);
        *///?}
    }

    public void centeredText(Component c, int centerX, int y, int argb) {
        text(c, centerX - textWidth(c) / 2, y, argb, true);
    }

    /** Draws text wrapped to {@code width}; returns the height used. */
    public int wrappedText(Component c, int x, int y, int width, int argb) {
        List<FormattedCharSequence> lines = font().split(c, width);
        for (int i = 0; i < lines.size(); i++) {
            text(lines.get(i), x, y + i * 10, argb, false);
        }
        return lines.size() * 10;
    }

    /** Draws a whole {@code texSize}² texture scaled into a {@code w}×{@code h} rectangle. */
    public void blit(Identifier texture, int x, int y, int w, int h, int texSize) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, w, h, texSize, texSize, texSize, texSize);
    }

    /** Draws a region of a texture scaled into a rectangle, tinted with an ARGB colour (alpha fades it). */
    public void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int regionW, int regionH,
                     int texW, int texH, int argb) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
    }

    public void push() {
        g.pose().pushMatrix();
    }

    public void pop() {
        g.pose().popMatrix();
    }

    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    public void scale(float s) {
        g.pose().scale(s, s);
    }

    /** Clips drawing to a rectangle in the current (transformed) coordinates. */
    public void scissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public void endScissor() {
        g.disableScissor();
    }
}
