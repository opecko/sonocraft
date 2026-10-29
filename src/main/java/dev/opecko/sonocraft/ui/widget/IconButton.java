package dev.opecko.sonocraft.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** A square button with an icon; the icon and its colour may change every frame (play/pause, shuffle on/off). */
public final class IconButton extends Widget {
    private final Supplier<Icon> icon;
    private final IntSupplier color;
    private final Runnable onClick;
    private IntSupplier background;

    public IconButton(Supplier<Icon> icon, IntSupplier color, Runnable onClick) {
        this.icon = icon;
        this.color = color;
        this.onClick = onClick;
    }

    public IconButton(Icon icon, Runnable onClick) {
        this(() -> icon, () -> UiColors.TEXT, onClick);
    }

    /** Draws the button as a filled square (the main play button). */
    public IconButton filled(IntSupplier background) {
        this.background = background;
        return this;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        if (!visible) {
            return;
        }
        boolean hover = active && contains(mouseX, mouseY);
        if (background != null) {
            // a square with its corner pixels cut, which reads as rounded at this size
            int c = background.getAsInt();
            g.fill(x + 1, y, x + width - 1, y + height, c);
            g.fill(x, y + 1, x + width, y + height - 1, c);
            if (hover) {
                g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x30FFFFFF);
            }
        } else if (hover) {
            g.fill(x, y, x + width, y + height, UiColors.ROW_HOVER);
        }
        int size = Math.min(width, height) >= 28 ? 24 : Icon.SIZE;
        int c = active ? color.getAsInt() : UiColors.TEXT_FAINT;
        icon.get().draw(g, x + (width - size) / 2, y + (height - size) / 2, size, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (active && button == InputConstants.MOUSE_BUTTON_LEFT && contains(mx, my)) {
            onClick.run();
            return true;
        }
        return false;
    }
}
