package dev.opecko.sonocraft.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import java.util.List;
import java.util.function.Supplier;

/**
 * A scrolling list that draws only the visible rows (PLAN §6.2). Asks for more items near the end (infinite
 * scroll), supports keyboard selection and, optionally, reordering rows by dragging their handle.
 */
public final class ScrollList<T> extends Widget {
    /** Draws one row. */
    public interface RowRenderer<T> {
        void render(Gfx g, T item, int index, int x, int y, int width, int height, boolean hover, long now);
    }

    /** A click on a row; {@code button} is an {@code InputConstants.MOUSE_BUTTON_*}. */
    public interface RowClick<T> {
        void click(T item, int index, int button, double mx, double my, boolean doubleClick);
    }

    /** Rows were dragged: move the row at {@code from} so it ends up at index {@code to}. */
    public interface RowMove {
        void move(int from, int to);
    }

    /** Width of the drag handle at the left of a row when reordering is on. */
    public static final int HANDLE = 14;

    private final Supplier<List<T>> items;
    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private final RowClick<T> onClick;
    private Runnable onNearEnd = () -> { };
    private RowMove onMove;
    private double scroll;
    private int selected = -1;
    private int dragFrom = -1;
    private double dragY;

    public ScrollList(Supplier<List<T>> items, int rowHeight, RowRenderer<T> renderer, RowClick<T> onClick) {
        this.items = items;
        this.rowHeight = rowHeight;
        this.renderer = renderer;
        this.onClick = onClick;
    }

    public ScrollList<T> onNearEnd(Runnable r) {
        this.onNearEnd = r;
        return this;
    }

    public ScrollList<T> reorderable(RowMove move) {
        this.onMove = move;
        return this;
    }

    public void resetScroll() {
        scroll = 0;
        selected = -1;
    }

    /** Scrolls so the row sits in the middle (the line being sung). */
    public void centerOn(int index) {
        scroll = (double) index * rowHeight - (height - rowHeight) / 2.0;
        clamp();
    }

    /** Scrolls so the row is in view, e.g. the playing queue entry. */
    public void scrollTo(int index) {
        double top = (double) index * rowHeight;
        if (top < scroll) {
            scroll = top;
        } else if (top + rowHeight > scroll + height) {
            scroll = top + rowHeight - height;
        }
        clamp();
    }

    private int total() {
        return items.get().size() * rowHeight;
    }

    private void clamp() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total() - height)));
    }

    private int indexAt(double my) {
        int i = (int) Math.floor((my - y + scroll) / rowHeight);
        return i >= 0 && i < items.get().size() ? i : -1;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        if (!visible) {
            return;
        }
        clamp();
        List<T> list = items.get();
        boolean scrollbar = total() > height;
        int rowWidth = width - (scrollbar ? 4 : 0);
        g.scissor(x, y, x + width, y + height);
        int first = (int) (scroll / rowHeight);
        int last = Math.min(list.size() - 1, (int) ((scroll + height) / rowHeight));
        int hovered = contains(mouseX, mouseY) && dragFrom < 0 ? indexAt(mouseY) : -1;
        for (int i = first; i <= last; i++) {
            int ry = y + i * rowHeight - (int) Math.round(scroll);
            if (i == selected) {
                g.fill(x, ry, x + rowWidth, ry + rowHeight, UiColors.ROW_HOVER);
            }
            if (i == dragFrom) {
                g.fill(x, ry, x + rowWidth, ry + rowHeight, 0x10FFFFFF);
                continue;
            }
            renderer.render(g, list.get(i), i, x, ry, rowWidth, rowHeight, i == hovered, now);
        }
        if (dragFrom >= 0) {
            int target = dropIndex();
            int ly = y + target * rowHeight - (int) Math.round(scroll);
            g.fill(x, ly - 1, x + rowWidth, ly + 1, UiColors.TEXT);
            renderer.render(g, list.get(dragFrom), dragFrom, x, (int) dragY - rowHeight / 2, rowWidth, rowHeight,
                    true, now);
        }
        g.endScissor();
        if (scrollbar) {
            int barH = Math.max(12, height * height / total());
            int barY = y + (int) ((height - barH) * (scroll / (total() - height)));
            g.fill(x + width - 3, y, x + width - 1, y + height, 0x18FFFFFF);
            g.fill(x + width - 3, barY, x + width - 1, barY + barH, 0x60FFFFFF);
        }
        if (scroll + height >= total() - rowHeight * 5) {
            onNearEnd.run();
        }
    }

    /** Where a dragged row would land: the gap nearest to the mouse. */
    private int dropIndex() {
        int size = items.get().size();
        int gap = (int) Math.round((dragY - y + scroll) / rowHeight);
        return Math.max(0, Math.min(size, gap));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (!contains(mx, my)) {
            return false;
        }
        int i = indexAt(my);
        if (i < 0) {
            return true;
        }
        selected = i;
        if (onMove != null && button == InputConstants.MOUSE_BUTTON_LEFT && mx < x + HANDLE) {
            dragFrom = i;
            dragY = my;
            return true;
        }
        onClick.click(items.get().get(i), i, button, mx, my, doubleClick);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        if (dragFrom < 0) {
            return false;
        }
        dragY = my;
        // scroll while dragging near the edges
        if (my < y + rowHeight) {
            scroll -= 4;
        } else if (my > y + height - rowHeight) {
            scroll += 4;
        }
        clamp();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragFrom < 0) {
            return false;
        }
        int from = dragFrom;
        int gap = dropIndex();
        dragFrom = -1;
        // dropping into the gap after a row moves it to that gap minus itself
        int to = gap > from ? gap - 1 : gap;
        if (to != from && onMove != null) {
            onMove.move(from, to);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        scroll -= amount * rowHeight * 2;
        clamp();
        return true;
    }

    /** Arrow keys move the selection, Enter activates it. Returns true when used. */
    public boolean keyPressed(int key) {
        int size = items.get().size();
        if (size == 0) {
            return false;
        }
        if (key == InputConstants.KEY_DOWN) {
            selected = Math.min(size - 1, selected + 1);
            scrollTo(selected);
            return true;
        }
        if (key == InputConstants.KEY_UP) {
            selected = Math.max(0, selected - 1);
            scrollTo(selected);
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && selected >= 0
                && selected < size) {
            onClick.click(items.get().get(selected), selected, InputConstants.MOUSE_BUTTON_LEFT, 0, 0, false);
            return true;
        }
        return false;
    }
}
