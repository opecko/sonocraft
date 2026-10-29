package dev.opecko.sonocraft.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;

/** A flat button with a label; {@code filled} draws it in the accent colour. */
public final class TextButton extends Widget {
    private Component label;
    private final Runnable onClick;
    private final IntSupplier accent;
    private final boolean filled;

    public TextButton(Component label, IntSupplier accent, boolean filled, Runnable onClick) {
        this.label = label;
        this.accent = accent;
        this.filled = filled;
        this.onClick = onClick;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    /** Width that fits the label. */
    public int preferredWidth(Gfx g) {
        return g.textWidth(label) + 12;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        if (!visible) {
            return;
        }
        boolean hover = active && contains(mouseX, mouseY);
        int bg = !active ? 0x20FFFFFF : filled ? accent.getAsInt() : hover ? 0x30FFFFFF : 0x18FFFFFF;
        g.fill(x, y, x + width, y + height, bg);
        if (hover && filled) {
            g.fill(x, y, x + width, y + height, 0x30FFFFFF);
        }
        int color = active ? UiColors.TEXT : UiColors.TEXT_FAINT;
        g.text(label, x + (width - g.textWidth(label)) / 2, y + (height - 8) / 2, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (active && visible && button == InputConstants.MOUSE_BUTTON_LEFT && contains(mx, my)) {
            onClick.run();
            return true;
        }
        return false;
    }
}
