package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.PodcastDetail;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.PagedLoader;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.IconButton;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.TextButton;
import net.minecraft.network.chat.Component;

/** A podcast show and its episodes, newest first (browse.podcast). */
public final class PodcastPage extends Page {
    private PodcastDetail detail;
    private final PagedLoader<Episode> loader;
    private final ScrollList<Episode> list;
    private final DetailHeader header = new DetailHeader();
    private double menuX;
    private double menuY;

    public PodcastPage(UiContext ui, String id) {
        super(ui);
        loader = new PagedLoader<>(cursor -> ui.api().browsePodcast(id, cursor, null).thenApply(p -> {
            detail = p.podcast();
            return new dev.opecko.sonocraft.api.model.Page<>(p.items(), p.nextCursor());
        }), Component.translatable("sonocraft.ui.empty"));
        list = new ScrollList<>(loader::items, ItemRow.HEIGHT, (g, e, i, x, y, w, h, hover, now) ->
                ItemRow.render(g, ui, e, x, y, w, h, hover, false, 0), this::click).onNearEnd(loader::loadMore);
        header.add(new TextButton(Component.translatable("sonocraft.ui.action.play"), () -> ui.accent().accent(), true,
                        () -> ItemActions.play(ui, detail.podcast().ref(), detail.title(), null, false)))
                .add(new IconButton(Icon.MORE, () -> ui.openMenu(ItemActions.menu(ui, detail.podcast(),
                        ItemActions.Origin.NONE, () -> { }), menuX, menuY)));
    }

    @Override
    public Component title() {
        return detail != null ? Component.literal(detail.title()) : Component.translatable("sonocraft.ui.kind.podcast");
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
        if (detail == null) {
            loader.renderStatus(g, x, y, width, height, mouseX, mouseY, now);
            return;
        }
        String sub = Component.translatable("sonocraft.ui.kind.podcast").getString()
                + (detail.author() != null ? " · " + detail.author() : "");
        header.render(g, ui, x, y, width, detail.thumbnailUrl(), false, Component.literal(detail.title()),
                Component.literal(sub), mouseX, mouseY, now);
        if (!loader.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            list.render(g, mouseX, mouseY, now);
        }
    }

    private void click(Episode e, int index, int button, double mx, double my, boolean doubleClick) {
        ItemActions.Origin origin = ItemActions.Origin.in(detail.podcast().ref(), detail.title());
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, e, origin, () -> { }), mx, my);
        } else {
            ItemActions.activate(ui, e, origin);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        menuX = mx;
        menuY = my;
        if (loader.mouseClicked(mx, my, button)) {
            return true;
        }
        return detail != null && (header.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button, doubleClick));
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
