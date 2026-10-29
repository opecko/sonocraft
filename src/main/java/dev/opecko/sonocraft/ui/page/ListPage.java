package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.api.model.Page;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.PagedLoader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.TextButton;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

/**
 * A paged list of items: the library sections (playlists, liked songs, albums, artists, podcasts, episodes for
 * later, history). {@code section} gives an optional group label (history's "Today"), shown where it changes.
 */
public class ListPage<T> extends dev.opecko.sonocraft.ui.page.Page {
    private static final int HEADER = 22;

    private final Component title;
    private final Function<T, Item> item;
    private final Function<T, String> section;
    private final PagedLoader<T> loader;
    private final ScrollList<T> list;
    private TextButton action;
    /** Items added here before the server lists them (a playlist just created). */
    private final java.util.List<T> extra = new java.util.ArrayList<>();
    private java.util.function.Function<T, String> extraId;

    public ListPage(UiContext ui, Component title, Function<String, CompletableFuture<Page<T>>> fetch,
                    Function<T, Item> item, Function<T, String> section) {
        super(ui);
        this.title = title;
        this.item = item;
        this.section = section;
        this.loader = new PagedLoader<>(fetch, Component.translatable("sonocraft.ui.empty"));
        this.list = new ScrollList<T>(this::items, ItemRow.HEIGHT, this::renderRow, this::click)
                .onNearEnd(loader::loadMore);
    }

    /** A button in the header (e.g. "New playlist"). */
    public ListPage<T> withAction(Component label, Runnable run) {
        action = new TextButton(label, () -> ui.accent().accent(), true, run);
        return this;
    }

    /** Shows an item at the top until the reloaded list contains it too. */
    public void addFirst(T value, java.util.function.Function<T, String> id) {
        extraId = id;
        extra.add(0, value);
    }

    private java.util.List<T> items() {
        java.util.List<T> loaded = loader.items();
        if (extra.isEmpty()) {
            return loaded;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        loaded.forEach(v -> ids.add(extraId.apply(v)));
        extra.removeIf(v -> ids.contains(extraId.apply(v)));
        java.util.List<T> out = new java.util.ArrayList<>(extra);
        out.addAll(loaded);
        return out;
    }

    public void reload() {
        list.resetScroll();
        loader.reload();
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        super.layout(x, y, width, height);
        list.setBounds(x, y + HEADER, width, height - HEADER);
    }

    @Override
    public void onShow() {
        loader.ensure();
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        g.text(title, x + 4, y + 6, UiColors.TEXT, false);
        if (action != null) {
            int w = action.preferredWidth(g);
            action.setBounds(x + width - w - 4, y + 2, w, 16);
            action.render(g, mouseX, mouseY, now);
        }
        if (!loader.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            list.render(g, mouseX, mouseY, now);
        }
    }

    private void renderRow(Gfx g, T value, int index, int x, int y, int w, int h, boolean hover, long now) {
        Item it = item.apply(value);
        if (it == null) {
            return;
        }
        ItemRow.render(g, ui, it, x, y, w, h, hover, false, 0);
        if (section != null) {
            String s = section.apply(value);
            String before = index > 0 ? section.apply(items().get(index - 1)) : null;
            if (s != null && !s.equals(before)) {
                int sw = g.textWidth(s);
                g.fill(x + w - sw - 70, y + 2, x + w - 66, y + 12, 0x60000000);
                g.text(s, x + w - sw - 68, y + 3, UiColors.TEXT_FAINT, false);
            }
        }
    }

    private void click(T value, int index, int button, double mx, double my, boolean doubleClick) {
        Item it = item.apply(value);
        if (it == null) {
            return;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, it, ItemActions.Origin.NONE, this::reload), mx, my);
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            ItemActions.activate(ui, it, ItemActions.Origin.NONE);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (action != null && action.mouseClicked(mx, my, button, doubleClick)) {
            return true;
        }
        return loader.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button, doubleClick);
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
