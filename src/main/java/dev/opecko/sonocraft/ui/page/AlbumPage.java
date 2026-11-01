package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.model.AlbumDetail;
import dev.opecko.sonocraft.api.model.Ref;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.Icon;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.Loader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.IconButton;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.TextButton;
import java.util.List;
import net.minecraft.network.chat.Component;

/** An album with all its tracks (browse.album): play, shuffle, queue, and each track starts the album there. */
public final class AlbumPage extends Page {
    private final Loader<AlbumDetail> loader;
    private final ScrollList<Track> list;
    private final DetailHeader header = new DetailHeader();

    public AlbumPage(UiContext ui, String id) {
        super(ui);
        loader = new Loader<>(() -> ui.api().browseAlbum(id));
        list = new ScrollList<>(this::tracks, ItemRow.HEIGHT, (g, t, i, x, y, w, h, hover, now) ->
                ItemRow.render(g, ui, t, x, y, w, h, hover, isPlaying(t), 0), this::click);
        header.add(new TextButton(Component.translatable("sonocraft.ui.action.play"), () -> ui.accent().accent(), true,
                        () -> ItemActions.play(ui, ref(), loader.value().title(), null, false)))
                .add(new TextButton(Component.translatable("sonocraft.ui.action.shuffle"), () -> 0, false,
                        () -> ItemActions.play(ui, ref(), loader.value().title(), null, true)))
                .add(new IconButton(Icon.MORE, this::menu));
    }

    private Ref ref() {
        return loader.value().album().ref();
    }

    private List<Track> tracks() {
        AlbumDetail d = loader.value();
        return d == null || d.tracks() == null ? List.of() : d.tracks();
    }

    private boolean isPlaying(Track t) {
        var item = ui.core().player.get().item();
        return item != null && item.id().equals(t.id());
    }

    private double menuX;
    private double menuY;

    private void menu() {
        ui.openMenu(ItemActions.menu(ui, loader.value().album(), ItemActions.Origin.NONE, () -> { }), menuX, menuY);
    }

    @Override
    public Component title() {
        AlbumDetail d = loader.value();
        return d != null ? Component.literal(d.title()) : Component.translatable("sonocraft.ui.kind.album");
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
        if (loader.renderStatus(g, x, y, width, height, mouseX, mouseY, now)) {
            return;
        }
        AlbumDetail d = loader.value();
        String artists = d.artists() == null ? "" : d.artists().stream().map(a -> a.name())
                .filter(n -> n != null && !n.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        String sub = Texts.dotted(artists, d.year() != null ? String.valueOf(d.year()) : null,
                Component.translatable("sonocraft.ui.tracks", d.trackCount()).getString(),
                d.durationMs() != null ? Texts.time(d.durationMs()) : null);
        header.render(g, ui, x, y, width, d.thumbnailUrl(), false, Component.literal(d.title()),
                Component.literal(sub.toString()), mouseX, mouseY, now);
        list.render(g, mouseX, mouseY, now);
    }

    private void click(Track t, int index, int button, double mx, double my, boolean doubleClick) {
        ItemActions.Origin origin = ItemActions.Origin.in(ref(), loader.value().title());
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, t, origin, () -> { }), mx, my);
        } else {
            ItemActions.activate(ui, t, origin);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        menuX = mx;
        menuY = my;
        if (loader.mouseClicked(mx, my, button)) {
            return true;
        }
        if (loader.state() != Loader.State.READY) {
            return false;
        }
        return header.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button, doubleClick);
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
