package dev.opecko.sonocraft.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * A horizontal 0–1 slider (seek bar, volume). While dragging it shows the dragged value. The seek bar sends it on
 * release; the volume also while dragging, at most every {@link #LIVE_MS} (rate class control is 10/s, PLAN §3.7).
 * After release it keeps showing the new value until Sonolith reports it, instead of jumping back meanwhile.
 */
public final class Slider extends Widget {
    private final DoubleSupplier value;
    private final DoubleConsumer onRelease;
    private final IntSupplier accent;
    private static final long LIVE_MS = 150;
    private static final long PENDING_MS = 2500;
    /** Extra pixels above and below the bar that still grab it. */
    private static final int GRAB = 4;

    private DoubleConsumer onChange;
    private boolean dragging;
    private double dragValue;
    private long lastLive;
    private double pending = -1;
    private long pendingAt;

    public Slider(DoubleSupplier value, IntSupplier accent, DoubleConsumer onRelease) {
        this.value = value;
        this.accent = accent;
        this.onRelease = onRelease;
    }

    /** Also sends the value while dragging (volume). */
    public Slider live() {
        this.onChange = onRelease;
        return this;
    }

    @Override
    public boolean contains(double mx, double my) {
        return visible && mx >= x - 2 && my >= y - GRAB && mx < x + width + 2 && my < y + height + GRAB;
    }

    public boolean isDragging() {
        return dragging;
    }

    /** The value shown: the dragged one while dragging. */
    public double shown() {
        if (dragging) {
            return dragValue;
        }
        double v = Math.max(0, Math.min(1, value.getAsDouble()));
        if (pending >= 0) {
            if (Math.abs(v - pending) < 0.02 || System.currentTimeMillis() - pendingAt > PENDING_MS) {
                pending = -1; // Sonolith caught up (or never will)
            } else {
                return pending;
            }
        }
        return v;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        if (!visible) {
            return;
        }
        boolean hover = active && (dragging || contains(mouseX, mouseY));
        int cy = y + height / 2;
        int barH = hover ? 4 : 2;
        g.fill(x, cy - barH / 2, x + width, cy - barH / 2 + barH, UiColors.TRACK);
        int filled = (int) Math.round(width * shown());
        g.fill(x, cy - barH / 2, x + filled, cy - barH / 2 + barH, active ? accent.getAsInt() : UiColors.TEXT_FAINT);
        if (hover) {
            g.fill(x + filled - 2, cy - 4, x + filled + 2, cy + 4, UiColors.TEXT);
        }
    }

    private double at(double mx) {
        return width <= 0 ? 0 : Math.max(0, Math.min(1, (mx - x) / width));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (active && button == InputConstants.MOUSE_BUTTON_LEFT && contains(mx, my)) {
            dragging = true;
            dragValue = at(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (dragging) {
            dragValue = at(mx);
            long now = System.currentTimeMillis();
            if (onChange != null && now - lastLive >= LIVE_MS) {
                lastLive = now;
                onChange.accept(dragValue);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            pending = at(mx);
            pendingAt = System.currentTimeMillis();
            onRelease.accept(pending);
            return true;
        }
        return false;
    }
}
