package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.model.ArtistDetail;
import dev.opecko.sonocraft.api.model.ArtistSection;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.Loader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** An artist page (browse.artist): its shelves (top songs, albums, singles, videos, related artists…) as one list. */
public final class ArtistPage extends Page {
    /** A list row: a shelf title or an item. */
    private record Row(String heading, Item item) {
    }

    private final Loader<ArtistDetail> loader;
    private final ScrollList<Row> list;
    private final DetailHeader header = new DetailHeader();
    private List<Row> rows = List.of();
    private ArtistDetail built;

    public ArtistPage(UiContext ui, String id) {
        super(ui);
        loader = new Loader<>(() -> ui.api().browseArtist(id));
        list = new ScrollList<>(this::rows, ItemRow.HEIGHT, (g, r, i, x, y, w, h, hover, now) -> {
            if (r.heading() != null) {
                g.text(r.heading(), x + 4, y + h - 12, UiColors.TEXT, false);
                g.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, UiColors.DIVIDER);
            } else {
                ItemRow.render(g, ui, r.item(), x, y, w, h, hover, false, 0);
            }
        }, this::click);
    }

    private List<Row> rows() {
        ArtistDetail d = loader.value();
        if (d != built) {
            built = d;
            List<Row> out = new ArrayList<>();
            if (d != null && d.sections() != null) {
                for (ArtistSection s : d.sections()) {
                    if (s == null || s.items() == null) {
                        continue;
                    }
                    List<Item> items = s.items().stream().filter(i -> i != null).toList();
                    if (!items.isEmpty()) {
                        out.add(new Row(s.title(), null));
                        items.forEach(i -> out.add(new Row(null, i)));
                    }
                }
            }
            rows = out;
        }
        return rows;
    }

    @Override
    public Component title() {
        ArtistDetail d = loader.value();
        return d != null ? Component.literal(d.name()) : Component.translatable("sonocraft.ui.kind.artist");
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
        ArtistDetail d = loader.value();
        String sub = Component.translatable("sonocraft.ui.kind.artist").getString()
                + (d.subscribersText() != null ? " · " + d.subscribersText() : "");
        header.render(g, ui, x, y, width, d.thumbnailUrl(), true, Component.literal(d.name()),
                Component.literal(sub), mouseX, mouseY, now);
        if (d.description() != null) {
            String first = d.description().lines().findFirst().orElse("");
            g.scissor(x + 68, y + 32, x + width - 4, y + 42);
            g.text(first, x + 68, y + 33, UiColors.TEXT_FAINT, false);
            g.endScissor();
        }
        list.render(g, mouseX, mouseY, now);
    }

    private void click(Row r, int index, int button, double mx, double my, boolean doubleClick) {
        if (r.item() == null) {
            return;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, r.item(), ItemActions.Origin.NONE, () -> { }), mx, my);
        } else {
            ItemActions.activate(ui, r.item(), ItemActions.Origin.NONE);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (loader.mouseClicked(mx, my, button)) {
            return true;
        }
        return loader.state() == Loader.State.READY && list.mouseClicked(mx, my, button, doubleClick);
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
