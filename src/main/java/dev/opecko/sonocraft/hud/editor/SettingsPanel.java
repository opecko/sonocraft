package dev.opecko.sonocraft.hud.editor;

import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.List;
import java.util.function.IntSupplier;

/** The box beside the HUD with the selected element's settings; scrolls when taller than the screen. */
public final class SettingsPanel {
    public static final int WIDTH = 160;
    private static final int HEADER = 16;

    private HudElement element;
    private List<Setting> settings = List.of();
    public int x;
    public int y;
    public int height;
    private int scroll;

    public HudElement element() {
        return element;
    }

    public void show(HudElement element, List<Setting> settings) {
        this.element = element;
        this.settings = settings;
        scroll = 0;
    }

    private int contentHeight() {
        int h = 0;
        for (Setting s : settings) {
            h += s.height() + 2;
        }
        return h;
    }

    /** Places the panel at {@code x}, from {@code top}, at most down to {@code bottom}. */
    public void place(int x, int top, int bottom) {
        this.x = x;
        this.y = top;
        this.height = Math.min(HEADER + contentHeight() + 4, bottom - top);
        scroll = Math.max(0, Math.min(scroll, HEADER + contentHeight() + 4 - height));
    }

    public boolean contains(double mx, double my) {
        return element != null && mx >= x && mx < x + WIDTH && my >= y && my < y + height;
    }

    public void render(Gfx g, int mouseX, int mouseY, long now, IntSupplier accent) {
        if (element == null) {
            return;
        }
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, UiColors.DIVIDER);
        g.fill(x, y, x + WIDTH, y + height, UiColors.WINDOW);
        g.fill(x, y, x + 2, y + HEADER, accent.getAsInt());
        g.text(element.label(), x + 6, y + 4, UiColors.TEXT, false);
        g.scissor(x, y + HEADER, x + WIDTH, y + height);
        int ry = y + HEADER + 2 - scroll;
        for (Setting s : settings) {
            s.x = x + 2;
            s.y = ry;
            s.width = WIDTH - 4;
            s.render(g, mouseX, mouseY, now, accent);
            ry += s.height() + 2;
        }
        g.endScissor();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return false;
        }
        if (my >= y + HEADER) {
            for (Setting s : settings) {
                if (s.mouseClicked(mx, my, button)) {
                    return true;
                }
            }
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        for (Setting s : settings) {
            if (s.mouseDragged(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        for (Setting s : settings) {
            if (s.mouseReleased(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        scroll = (int) Math.max(0, Math.min(HEADER + contentHeight() + 4 - height, scroll - amount * 20));
        return true;
    }
}
