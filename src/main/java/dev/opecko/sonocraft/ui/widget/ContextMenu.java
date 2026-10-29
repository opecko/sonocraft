package dev.opecko.sonocraft.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** A popup list of actions at the mouse. Disabled entries are shown greyed with the reason as a hint. */
public final class ContextMenu extends Widget {
    private static final int ROW = 14;

    private record Entry(Component label, Runnable action, Component disabledReason) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private Runnable onClose = () -> { };

    public ContextMenu add(Component label, Runnable action) {
        entries.add(new Entry(label, action, null));
        return this;
    }

    /** An entry that can't be used now; {@code reason} explains why (e.g. a missing permission). */
    public ContextMenu addDisabled(Component label, Component reason) {
        entries.add(new Entry(label, null, reason));
        return this;
    }

    public ContextMenu separator() {
        entries.add(new Entry(null, null, null));
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void onClose(Runnable r) {
        this.onClose = r;
    }

    /** Sizes the menu and places it at the mouse, kept inside the screen. */
    public void open(Gfx g, double mx, double my, int screenWidth, int screenHeight) {
        int w = 60;
        for (Entry e : entries) {
            if (e.label() != null) {
                w = Math.max(w, g.textWidth(e.label()) + 16);
            }
        }
        int h = 4;
        for (Entry e : entries) {
            h += e.label() == null ? 5 : ROW;
        }
        setBounds((int) Math.min(mx, screenWidth - w - 2), (int) Math.min(my, screenHeight - h - 2), w, h);
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, UiColors.DIVIDER);
        g.fill(x, y, x + width, y + height, UiColors.MENU);
        int ry = y + 2;
        Component hint = null;
        for (Entry e : entries) {
            if (e.label() == null) {
                g.fill(x + 4, ry + 2, x + width - 4, ry + 3, UiColors.DIVIDER);
                ry += 5;
                continue;
            }
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= ry && mouseY < ry + ROW;
            if (hover && e.action() != null) {
                g.fill(x, ry, x + width, ry + ROW, UiColors.ROW_HOVER);
            }
            if (hover && e.disabledReason() != null) {
                hint = e.disabledReason();
            }
            g.text(e.label(), x + 8, ry + 3, e.action() != null ? UiColors.TEXT : UiColors.TEXT_FAINT, false);
            ry += ROW;
        }
        if (hint != null) {
            int hw = g.textWidth(hint) + 8;
            int hx = Math.min(x + width + 4, g.width() - hw - 2);
            g.fill(hx, mouseY, hx + hw, mouseY + 13, UiColors.MENU);
            g.text(hint, hx + 4, mouseY + 3, UiColors.TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (!contains(mx, my)) {
            onClose.run();
            return true; // a click outside closes the menu and does nothing else
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
            return true;
        }
        int ry = y + 2;
        for (Entry e : entries) {
            int h = e.label() == null ? 5 : ROW;
            if (my >= ry && my < ry + h && e.action() != null) {
                onClose.run();
                e.action().run();
                return true;
            }
            ry += h;
        }
        return true;
    }
}
