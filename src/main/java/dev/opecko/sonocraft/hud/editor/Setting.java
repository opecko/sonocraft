package dev.opecko.sonocraft.hud.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.widget.Slider;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** One row of the HUD editor's settings panel. Values are read and written live, so the preview follows. */
public abstract class Setting {
    protected final Component label;
    int x;
    int y;
    int width;

    Setting(Component label) {
        this.label = label;
    }

    abstract int height();

    abstract void render(Gfx g, int mouseX, int mouseY, long now, IntSupplier accent);

    boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    boolean mouseDragged(double mx, double my, int button) {
        return false;
    }

    boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    boolean hovered(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height();
    }

    /** A checkbox; clicking anywhere on the row flips it. */
    static final class Toggle extends Setting {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        Toggle(Component label, BooleanSupplier get, Consumer<Boolean> set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        @Override
        int height() {
            return 14;
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now, IntSupplier accent) {
            if (hovered(mouseX, mouseY)) {
                g.fill(x, y, x + width, y + height(), UiColors.ROW_HOVER);
            }
            g.text(label, x + 3, y + 3, UiColors.TEXT, false);
            int bx = x + width - 13;
            boolean on = get.getAsBoolean();
            g.fill(bx, y + 2, bx + 10, y + 12, on ? accent.getAsInt() : 0x40FFFFFF);
            if (on) {
                Icon.CHECK.draw(g, bx - 1, y + 1, 0xFFFFFFFF);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && hovered(mx, my)) {
                set.accept(!get.getAsBoolean());
                return true;
            }
            return false;
        }
    }

    /** A labelled slider with its value; changes apply while dragging. */
    static final class Range extends Setting {
        private final double min;
        private final double max;
        private final double step;
        private final Supplier<Double> get;
        private final Consumer<Double> set;
        private final DoubleFunction<String> format;
        private Slider slider;

        Range(Component label, double min, double max, double step, Supplier<Double> get, Consumer<Double> set,
              DoubleFunction<String> format) {
            super(label);
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.format = format;
        }

        private double snap(double fraction) {
            double v = min + fraction * (max - min);
            return Math.max(min, Math.min(max, Math.round(v / step) * step));
        }

        @Override
        int height() {
            return 24;
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now, IntSupplier accent) {
            if (slider == null) {
                slider = new Slider(() -> (get.get() - min) / (max - min), accent, f -> set.accept(snap(f))).live();
            }
            g.text(label, x + 3, y + 2, UiColors.TEXT, false);
            double shown = slider.isDragging() ? snap(slider.shown()) : get.get();
            String value = format.apply(shown);
            g.text(value, x + width - 3 - g.textWidth(value), y + 2, UiColors.TEXT_DIM, false);
            slider.setBounds(x + 3, y + 14, width - 6, 8);
            slider.render(g, mouseX, mouseY, now);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            return slider != null && slider.mouseClicked(mx, my, button, false);
        }

        @Override
        boolean mouseDragged(double mx, double my, int button) {
            return slider != null && slider.mouseDragged(mx, my, button);
        }

        @Override
        boolean mouseReleased(double mx, double my, int button) {
            return slider != null && slider.mouseReleased(mx, my, button);
        }
    }

    /** Picks one of an enum's values: left click goes to the next, right click to the previous. */
    static final class Choice<E extends Enum<E>> extends Setting {
        private final Class<E> type;
        private final Supplier<E> get;
        private final Consumer<E> set;
        private final String keyPrefix;

        Choice(Component label, Class<E> type, Supplier<E> get, Consumer<E> set, String keyPrefix) {
            super(label);
            this.type = type;
            this.get = get;
            this.set = set;
            this.keyPrefix = keyPrefix;
        }

        private Component value() {
            return Component.translatable(keyPrefix + get.get().name().toLowerCase(Locale.ROOT));
        }

        /** True when the label and the value don't fit on one line: the value then goes on a second line. */
        private boolean wraps() {
            Font font = Minecraft.getInstance().font;
            return width > 0 && font.width(label) + 8 + font.width(value()) + 13 > width;
        }

        @Override
        int height() {
            return wraps() ? 24 : 14;
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now, IntSupplier accent) {
            boolean hover = hovered(mouseX, mouseY);
            if (hover) {
                g.fill(x, y, x + width, y + height(), UiColors.ROW_HOVER);
            }
            g.text(label, x + 3, y + 3, UiColors.TEXT, false);
            Component value = value();
            int vy = wraps() ? y + 13 : y + 3;
            g.text(value, x + width - 13 - g.textWidth(value), vy, hover ? accent.getAsInt() : UiColors.TEXT_DIM, false);
            Icon.CHEVRON_RIGHT.draw(g, x + width - 12, vy - 2, hover ? UiColors.TEXT : UiColors.TEXT_FAINT);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!hovered(mx, my)) {
                return false;
            }
            E[] values = type.getEnumConstants();
            int i = get.get().ordinal();
            int step = button == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;
            set.accept(values[(i + step + values.length) % values.length]);
            return true;
        }
    }
}
