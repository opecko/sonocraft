package dev.opecko.sonocraft.ui.widget;

import dev.opecko.sonocraft.compat.Gfx;

/**
 * A lightweight UI component drawn with {@link Gfx}. Bounds are GUI pixels. Event methods return true when they
 * used the event. Mouse buttons are compared with {@code InputConstants.MOUSE_BUTTON_*} (26.3 uses SDL numbers).
 */
public abstract class Widget {
    public int x;
    public int y;
    public int width;
    public int height;
    public boolean visible = true;
    public boolean active = true;

    public Widget setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && my >= y && mx < x + width && my < y + height;
    }

    public abstract void render(Gfx g, int mouseX, int mouseY, long now);

    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }
}
