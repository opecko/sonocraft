package dev.opecko.sonocraft.ui.page;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.model.QueueEntry;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.Loader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ContextMenu;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.TextButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The queue (queue.get). The whole queue is loaded and refreshed in the background on queue.changed, so the list
 * keeps its place instead of reloading. Moves and removals show at once and are then confirmed by Sonolith.
 * Entries are changed by entryId, so the right copy of a song queued twice moves.
 */
public final class QueuePage extends Page {
    private static final int HEADER = 22;
    /** Enough for any queue a player looks through; Sonolith allows 5000. */
    private static final int MAX_LOADED = 1000;

    private record Summary(Integer currentIndex, int length, boolean autoplay) {
    }

    /** A loaded queue: entries, current entry and the autoplay switch. */
    private record Snapshot(List<QueueEntry> entries, String currentEntryId, int length, boolean autoplay) {
    }

    private final Loader<Snapshot> first;
    private List<QueueEntry> entries = List.of();
    private String currentEntryId;
    private int length;
    private boolean autoplayOn;
    private boolean dirty;
    private boolean refreshing;
    private int generation;
    private boolean scrolledToCurrent;
    private boolean appliedFirst;

    private final ScrollList<QueueEntry> list;
    private final TextButton autoplay;
    private final TextButton clear;

    public QueuePage(UiContext ui) {
        super(ui);
        first = new Loader<>(this::loadAll);
        list = new ScrollList<>(() -> entries, ItemRow.HEIGHT, this::renderRow, this::click).reorderable(this::move);
        autoplay = new TextButton(Component.empty(), () -> ui.accent().accent(), false, () -> {
            boolean on = !autoplayOn;
            autoplayOn = on;
            ItemActions.call(ui, () -> ui.api().queueSetAutoplay(on), r -> autoplayOn = r.queue().autoplay());
        });
        clear = new TextButton(Component.translatable("sonocraft.ui.queue.clear"), () -> 0, false, () -> ui.confirm(
                Component.translatable("sonocraft.ui.queue.clear"),
                Component.translatable("sonocraft.ui.queue.clear.message"),
                () -> ItemActions.call(ui, () -> ui.api().queueClear(), r -> dirty = true)));
    }

    /** Loads every page of the queue (up to {@link #MAX_LOADED} entries). */
    private CompletableFuture<Snapshot> loadAll() {
        List<QueueEntry> acc = new ArrayList<>();
        Summary[] summary = new Summary[1];
        return loadFrom(null, acc, summary).thenApply(v -> {
            Summary s = summary[0];
            String current = null;
            if (s != null && s.currentIndex() != null) {
                for (QueueEntry e : acc) {
                    if (e.index() == s.currentIndex()) {
                        current = e.entryId();
                    }
                }
            }
            return new Snapshot(List.copyOf(acc), current, s != null ? s.length() : acc.size(),
                    s != null && s.autoplay());
        });
    }

    private CompletableFuture<Void> loadFrom(String cursor, List<QueueEntry> acc, Summary[] summary) {
        return ui.api().queueGet(null, cursor, 100).thenCompose(page -> {
            if (cursor == null) {
                summary[0] = new Summary(page.currentIndex(), page.length(), page.autoplay());
            }
            page.items().stream().filter(Objects::nonNull).forEach(acc::add);
            if (page.nextCursor() != null && acc.size() < MAX_LOADED) {
                return loadFrom(page.nextCursor(), acc, summary);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private void apply(Snapshot s) {
        entries = s.entries();
        currentEntryId = s.currentEntryId();
        length = s.length();
        autoplayOn = s.autoplay();
    }

    /** Reloads in the background and swaps the list in when done; the old list stays meanwhile. */
    private void refresh() {
        dirty = false;
        refreshing = true;
        int gen = ++generation;
        loadAll().whenCompleteAsync((s, e) -> {
            refreshing = false;
            if (gen == generation && e == null) {
                apply(s);
            }
        }, Minecraft.getInstance());
    }

    @Override
    public Component title() {
        return Component.translatable("sonocraft.ui.tab.queue");
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        super.layout(x, y, width, height);
        list.setBounds(x, y + HEADER, width, height - HEADER);
    }

    @Override
    public void onShow() {
        scrolledToCurrent = false;
        if (first.state() == Loader.State.READY) {
            refresh(); // the queue changed while the page was away; show the old one until the new is here
        } else {
            first.reload();
        }
    }

    @Override
    public void onEvent(String method, JsonObject params) {
        if (method.equals("queue.changed")) {
            dirty = true;
        }
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        if (!appliedFirst && first.state() == Loader.State.READY) {
            appliedFirst = true;
            apply(first.value());
        }
        if (dirty && !refreshing && first.state() == Loader.State.READY) {
            refresh();
        }
        // header: where the music comes from, or how much is queued
        String from = ui.core().playingFrom.get();
        Component heading = from != null
                ? Component.translatable("sonocraft.ui.queue.playing_from", from)
                : Component.translatable("sonocraft.ui.queue.count", length);
        autoplay.setLabel(Component.translatable(autoplayOn ? "sonocraft.ui.queue.autoplay_on"
                : "sonocraft.ui.queue.autoplay_off"));
        int cw = clear.preferredWidth(g);
        int aw = autoplay.preferredWidth(g);
        clear.setBounds(x + width - cw - 4, y + 2, cw, 16);
        autoplay.setBounds(clear.x - aw - 4, y + 2, aw, 16);
        g.scissor(x, y, autoplay.x - 4, y + HEADER);
        g.text(heading, x + 4, y + 6, from != null ? ui.accent().accent() : UiColors.TEXT_DIM, false);
        g.endScissor();
        if (first.state() == Loader.State.READY) {
            autoplay.render(g, mouseX, mouseY, now);
            clear.render(g, mouseX, mouseY, now);
        }
        if (first.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            return;
        }
        if (entries.isEmpty()) {
            Component empty = Component.translatable("sonocraft.ui.queue.empty");
            g.text(empty, x + (width - g.textWidth(empty)) / 2, list.y + list.height / 2 - 4, UiColors.TEXT_DIM, false);
            return;
        }
        if (!scrolledToCurrent) {
            scrolledToCurrent = true;
            int current = indexOf(currentEntryId);
            if (current >= 0) {
                list.scrollTo(Math.min(current + 3, entries.size() - 1));
            }
        }
        list.render(g, mouseX, mouseY, now);
    }

    private int indexOf(String entryId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).entryId().equals(entryId)) {
                return i;
            }
        }
        return -1;
    }

    private void renderRow(Gfx g, QueueEntry e, int index, int x, int y, int w, int h, boolean hover, long now) {
        if (e.item() == null) {
            return;
        }
        boolean current = e.entryId().equals(currentEntryId);
        ItemRow.render(g, ui, e.item(), x, y, w - (hover ? 18 : 0), h, hover, current, ScrollList.HANDLE);
        Icon.DRAG.draw(g, x + 1, y + (h - Icon.SIZE) / 2, hover ? UiColors.TEXT_DIM : 0x40FFFFFF);
        if (e.autoplay()) {
            Component tag = Component.translatable("sonocraft.ui.queue.autoplay_tag");
            g.text(tag, x + w - g.textWidth(tag) - (hover ? 24 : 50), y + 3, UiColors.TEXT_FAINT, false);
        }
        if (hover) {
            Icon.CLOSE.draw(g, x + w - 15, y + (h - Icon.SIZE) / 2, UiColors.TEXT_DIM);
        }
    }

    private void click(QueueEntry e, int index, int button, double mx, double my, boolean doubleClick) {
        if (e.item() == null) {
            return;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ContextMenu m = new ContextMenu();
            m.add(Component.translatable("sonocraft.ui.action.play_now"), () -> playNow(e));
            m.add(Component.translatable("sonocraft.ui.action.move_next"), () -> moveAfterCurrent(e, null));
            m.add(Component.translatable("sonocraft.ui.action.remove_from_queue"), () -> remove(e));
            m.separator();
            ItemActions.fill(m, ui, e.item(), ItemActions.Origin.NONE, () -> { });
            ui.openMenu(m, mx, my);
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (mx >= list.x + list.width - 22) {
                remove(e);
            } else if (!e.entryId().equals(currentEntryId)) {
                playNow(e);
            }
        }
    }

    private void remove(QueueEntry e) {
        List<QueueEntry> next = new ArrayList<>(entries);
        next.removeIf(x -> x.entryId().equals(e.entryId()));
        entries = next;
        length = Math.max(0, length - 1);
        ItemActions.call(ui, () -> ui.api().queueRemove(List.of(e.entryId())), r -> dirty = true);
    }

    /** Most skips one click may send in a row (rate class control allows bursts of 20). */
    private static final int MAX_SKIPS = 15;

    /**
     * Sonolith has no "play this entry". A later entry is reached by skipping forward, so the queue keeps its
     * order; an earlier one (or one very far ahead) is moved right after the current entry and skipped to.
     */
    private void playNow(QueueEntry e) {
        int current = indexOf(currentEntryId);
        int target = indexOf(e.entryId());
        int skips = target - current;
        if (current >= 0 && skips >= 1 && skips <= MAX_SKIPS) {
            skip(skips);
        } else {
            moveAfterCurrent(e, () -> skip(1));
        }
    }

    /** Calls player.next {@code times} times, one after another. */
    private void skip(int times) {
        if (times <= 0) {
            return;
        }
        ItemActions.call(ui, () -> ui.api().next(), v -> skip(times - 1));
    }

    private void moveAfterCurrent(QueueEntry e, Runnable then) {
        int current = indexOf(currentEntryId);
        int from = indexOf(e.entryId());
        if (from < 0) {
            return;
        }
        int target = from > current ? current + 1 : current;
        if (from == target) {
            if (then != null) {
                then.run();
            }
            return;
        }
        move(from, target, then);
    }

    private void move(int from, int to) {
        move(from, to, null);
    }

    /** Moves the row at once, then asks Sonolith; queue.changed brings the confirmed order. */
    private void move(int from, int to, Runnable then) {
        if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size() || from == to) {
            return;
        }
        List<QueueEntry> next = new ArrayList<>(entries);
        QueueEntry moved = next.remove(from);
        next.add(to, moved);
        entries = next;
        // the list is the queue from its start, so a list position is a queue index
        ItemActions.call(ui, () -> ui.api().queueMove(moved.entryId(), to), r -> {
            dirty = true;
            if (then != null) {
                then.run();
            }
        });
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (first.state() == Loader.State.READY && (autoplay.mouseClicked(mx, my, button, doubleClick)
                || clear.mouseClicked(mx, my, button, doubleClick))) {
            return true;
        }
        return first.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button, doubleClick);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button) {
        return list.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return list.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key, boolean control) {
        return list.keyPressed(key);
    }
}
