package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.model.Album;
import dev.opecko.sonocraft.api.model.Artist;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.Podcast;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.compat.Gfx;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Draws an item as a list row: thumbnail, title, a line about it, and its length. */
public final class ItemRow {
    public static final int HEIGHT = 28;
    private static final int THUMB = 22;

    private ItemRow() {
    }

    public static void render(Gfx g, UiContext ui, Item item, int x, int y, int width, int height, boolean hover,
                              boolean current, int indent) {
        if (hover) {
            g.fill(x, y, x + width, y + height, UiColors.ROW_HOVER);
        }
        if (current) {
            g.fill(x, y, x + 2, y + height, ui.accent().accent());
        }
        int tx = x + indent + 4;
        int ty = y + (height - THUMB) / 2;
        ArtTextures.Art art = ui.thumbnail(item.thumbnailUrl(), THUMB);
        if (art != null) {
            // artists have round pictures, like in YouTube Music
            int radius = item instanceof Artist ? art.size() / 2 : art.size() / 10;
            g.blit(ui.core().art.rounded(art, radius), tx, ty, THUMB, THUMB, 0, 0, art.size(), art.size(), art.size(),
                    art.size(), 0xFFFFFFFF);
        } else {
            g.fill(tx, ty, tx + THUMB, ty + THUMB, ui.accent().dim());
        }
        String right = right(item);
        int rightW = right.isEmpty() ? 0 : g.textWidth(right) + 8;
        int textX = tx + THUMB + 6;
        int textW = Math.max(10, x + width - textX - rightW - 4);
        g.scissor(textX, y, textX + textW, y + height);
        g.text(item.displayTitle(), textX, y + 5, current ? ui.accent().bright() : UiColors.TEXT, false);
        g.text(subtitle(item), textX, y + 16, UiColors.TEXT_DIM, false);
        g.endScissor();
        if (!right.isEmpty()) {
            g.text(right, x + width - rightW + 2, y + (height - 8) / 2, UiColors.TEXT_FAINT, false);
        }
    }

    /** The second line: who made it, and for collections what kind of thing it is. */
    public static Component subtitle(Item item) {
        if (item instanceof Track t) {
            return Component.literal(Texts.dotted(t.artistNames(", "), t.album() != null ? t.album().title() : null));
        }
        if (item instanceof Episode e) {
            String by = e.podcast() != null && e.podcast().title() != null ? e.podcast().title()
                    : e.author() != null ? e.author() : "";
            return Component.literal(Texts.dotted(by, e.publishedText()));
        }
        String kind;
        String detail;
        if (item instanceof Album a) {
            kind = a.albumType() != null ? "sonocraft.ui.kind." + a.albumType().name().toLowerCase(Locale.ROOT)
                    : "sonocraft.ui.kind.album";
            String artists = a.artists() == null ? "" : a.artists().stream().map(c -> c.name())
                    .filter(n -> n != null && !n.isBlank()).collect(java.util.stream.Collectors.joining(", "));
            detail = Texts.dotted(artists, a.year() != null ? String.valueOf(a.year()) : null);
        } else if (item instanceof Playlist p) {
            kind = "sonocraft.ui.kind.playlist";
            detail = p.author() != null ? p.author() : "";
        } else if (item instanceof Artist a) {
            kind = "sonocraft.ui.kind.artist";
            detail = a.subscribersText() != null ? a.subscribersText() : "";
        } else if (item instanceof Podcast p) {
            kind = "sonocraft.ui.kind.podcast";
            detail = p.author() != null ? p.author() : "";
        } else {
            return Component.empty();
        }
        Component k = Component.translatable(kind);
        return detail.isEmpty() ? k : k.copy().append(" · " + detail);
    }

    private static String right(Item item) {
        if (item instanceof Track t && t.durationMs() != null) {
            return Texts.time(t.durationMs());
        }
        if (item instanceof Episode e && e.durationMs() != null) {
            return Texts.time(e.durationMs());
        }
        if (item instanceof Playlist p && p.trackCount() != null) {
            return String.valueOf(p.trackCount());
        }
        return "";
    }
}
