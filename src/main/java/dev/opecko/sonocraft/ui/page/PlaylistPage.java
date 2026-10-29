package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.PlaylistDetail;
import dev.opecko.sonocraft.api.model.PlaylistEntry;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.PagedLoader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ContextMenu;
import dev.opecko.sonocraft.ui.widget.IconButton;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.TextButton;
import net.minecraft.network.chat.Component;

/**
 * A playlist, page by page (playlist.get). Entries play the playlist from there; the owner can rename, delete and
 * remove entries (by entry id, so the right copy of a song repeated in the list goes).
 */
public final class PlaylistPage extends Page {
    private Playlist playlist;
    private PlaylistDetail detail;
    private final PagedLoader<PlaylistEntry> loader;
    private final ScrollList<PlaylistEntry> list;
    private final DetailHeader header = new DetailHeader();
    private double menuX;
    private double menuY;

    public PlaylistPage(UiContext ui, Playlist playlist) {
        super(ui);
        this.playlist = playlist;
        loader = new PagedLoader<>(cursor -> ui.api().playlistGet(playlist.id(), cursor, null).thenApply(p -> {
            detail = p.playlist();
            if (detail != null) {
                this.playlist = detail.playlist();
            }
            return new dev.opecko.sonocraft.api.model.Page<>(p.items(), p.nextCursor());
        }), Component.translatable("sonocraft.ui.empty"));
        list = new ScrollList<>(loader::items, ItemRow.HEIGHT, (g, e, i, x, y, w, h, hover, now) -> {
            if (e.item() != null) {
                ItemRow.render(g, ui, e.item(), x, y, w, h, hover, false, 0);
            }
        }, this::click).onNearEnd(loader::loadMore);
        header.add(new TextButton(Component.translatable("sonocraft.ui.action.play"), () -> ui.accent().accent(), true,
                        () -> ItemActions.play(ui, this.playlist.ref(), this.playlist.title(), null, false)))
                .add(new TextButton(Component.translatable("sonocraft.ui.action.shuffle"), () -> 0, false,
                        () -> ItemActions.play(ui, this.playlist.ref(), this.playlist.title(), null, true)))
                .add(new IconButton(Icon.MORE, this::menu));
    }

    private void menu() {
        ContextMenu m = ItemActions.menu(ui, playlist, ItemActions.Origin.NONE, this::afterChange);
        ui.openMenu(m, menuX, menuY);
    }

    private void afterChange() {
        // renamed or deleted: a deleted playlist has nothing left to show
        loader.reload();
    }

    public String playlistId() {
        return playlist.id();
    }

    @Override
    public Component title() {
        return Component.literal(playlist.title());
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        super.layout(x, y, width, height);
        list.setBounds(x, y + DetailHeader.HEIGHT, width, height - DetailHeader.HEIGHT);
    }

    @Override
    public void onShow() {
        loader.ensure();
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        StringBuilder sub = new StringBuilder(Component.translatable("sonocraft.ui.kind.playlist").getString());
        if (playlist.author() != null) {
            sub.append(" · ").append(playlist.author());
        }
        if (playlist.trackCount() != null) {
            sub.append(" · ").append(Component.translatable("sonocraft.ui.tracks", playlist.trackCount()).getString());
        }
        header.render(g, ui, x, y, width, playlist.thumbnailUrl(), false, Component.literal(playlist.title()),
                Component.literal(sub.toString()), mouseX, mouseY, now);
        if (!loader.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            list.render(g, mouseX, mouseY, now);
        }
        if (detail != null && detail.description() != null && list.height > 200) {
            g.text(detail.description(), x + width - 4 - g.textWidth(detail.description()), y + 4, UiColors.TEXT_FAINT,
                    false);
        }
    }

    private void click(PlaylistEntry e, int index, int button, double mx, double my, boolean doubleClick) {
        if (e.item() == null) {
            return;
        }
        ItemActions.Origin origin = new ItemActions.Origin(playlist.ref(), playlist.title(), playlist, e.entryId());
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, e.item(), origin, loader::reload), mx, my);
        } else {
            ItemActions.activate(ui, e.item(), origin);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        menuX = mx;
        menuY = my;
        return header.mouseClicked(mx, my, button) || loader.mouseClicked(mx, my, button)
                || list.mouseClicked(mx, my, button, doubleClick);
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
