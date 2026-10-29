package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.LyricLine;
import dev.opecko.sonocraft.api.model.LyricsResult;
import dev.opecko.sonocraft.api.model.LyricsTier;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.state.PlayerSnapshot;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.Loader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The current item large, with all of its lyrics (lyrics.get): the line being sung is highlighted and kept in
 * view, and clicking a timed line seeks there. Unsynced lyrics are shown as plain text.
 */
public final class NowPlayingPage extends Page {
    private static final int ROW = 11;
    /** After the user scrolls the lyrics, wait this long before following the song again. */
    private static final long MANUAL_SCROLL_MS = 4000;

    /** One wrapped piece of a lyrics line. */
    private record LyricRow(int line, FormattedCharSequence text, Long startMs) {
    }

    private String trackId;
    private Loader<LyricsResult> lyrics;
    private List<LyricRow> rows = List.of();
    private LyricsResult rowsFor;
    private int rowsWidth;
    private final ScrollList<LyricRow> list;
    private long scrolledAt;
    private int followedLine = Integer.MIN_VALUE;
    private int artSize;

    public NowPlayingPage(UiContext ui) {
        super(ui);
        list = new ScrollList<>(() -> rows, ROW, this::renderRow, this::clickRow);
    }

    @Override
    public Component title() {
        return Component.translatable("sonocraft.ui.tab.now_playing");
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        super.layout(x, y, width, height);
        artSize = Math.max(48, Math.min(height - 44, Math.min(width * 2 / 5, 140)));
        int lx = x + artSize + 16;
        list.setBounds(lx, y + 4, x + width - lx - 4, height - 18);
    }

    private void followTrack(MediaItem item) {
        String id = item instanceof Track ? item.id() : null;
        if (Objects.equals(id, trackId)) {
            return;
        }
        trackId = id;
        rows = List.of();
        rowsFor = null;
        followedLine = Integer.MIN_VALUE;
        list.resetScroll();
        lyrics = id == null ? null : new Loader<>(() -> ui.api().lyricsGet(id));
        if (lyrics != null && ui.core().session.status().has(Scope.LYRICS_READ)) {
            lyrics.ensure();
        } else if (lyrics != null) {
            lyrics.reload(); // fails with PermissionDenied locally and shows "Allow"
        }
    }

    @Override
    public void onEvent(String method, JsonObject params) {
        if (method.equals("lyrics.changed") && lyrics != null) {
            lyrics.reload(); // better lyrics were found for the current track
        }
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        PlayerSnapshot p = ui.core().player.get();
        MediaItem item = p.item();
        if (item == null) {
            Component none = Component.translatable("sonocraft.hud.nothing_playing");
            g.text(none, x + (width - g.textWidth(none)) / 2, y + height / 2 - 4, UiColors.TEXT_DIM, false);
            return;
        }
        followTrack(item);
        // artwork and titles
        int ax = x + 8;
        int ay = y + 8;
        // its own texture at the size shown here; the HUD's smaller one until it has loaded
        ArtTextures.Art art = ui.thumbnail(item.thumbnailUrl(), artSize);
        if (art == null) {
            art = ui.core().artTracker.current();
        }
        if (art != null) {
            g.blit(ui.core().art.rounded(art, art.size() / 16), ax, ay, artSize, artSize, 0, 0, art.size(), art.size(),
                    art.size(), art.size(), 0xFFFFFFFF);
        } else {
            g.fill(ax, ay, ax + artSize, ay + artSize, ui.accent().dim());
        }
        g.scissor(ax, ay + artSize, ax + artSize, y + height);
        g.text(item.displayTitle(), ax, ay + artSize + 6, UiColors.TEXT, false);
        g.text(ItemRow.subtitle(item), ax, ay + artSize + 17, UiColors.TEXT_DIM, false);
        g.endScissor();

        if (item instanceof Episode) {
            Component text = Component.translatable("sonocraft.ui.lyrics.episode");
            g.text(text, list.x, list.y + 4, UiColors.TEXT_FAINT, false);
            return;
        }
        if (lyrics == null || lyrics.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            return;
        }
        LyricsResult r = lyrics.value();
        if (r.status() != LyricsResult.Status.FOUND || r.lyrics() == null) {
            Component text = Component.translatable(r.status() == LyricsResult.Status.INSTRUMENTAL
                    ? "sonocraft.ui.lyrics.instrumental" : "sonocraft.ui.lyrics.none");
            g.text(text, list.x + (list.width - g.textWidth(text)) / 2, list.y + list.height / 2 - 4,
                    UiColors.TEXT_DIM, false);
            return;
        }
        buildRows(g, r);
        long pos = p.positionAt(now);
        int current = currentLine(r, pos);
        if (current != followedLine && now - scrolledAt > MANUAL_SCROLL_MS) {
            followedLine = current;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).line() == current) {
                    list.centerOn(i);
                    break;
                }
            }
        }
        list.render(g, mouseX, mouseY, now);
        String source = r.lyrics().attribution() != null ? r.lyrics().attribution() : r.lyrics().providerName();
        if (source != null) {
            Component credit = Component.translatable("sonocraft.ui.lyrics.source", source);
            g.text(credit, list.x, y + height - 11, UiColors.TEXT_FAINT, false);
        }
    }

    private int currentLine(LyricsResult r, long pos) {
        if (r.lyrics().tier() == LyricsTier.UNSYNCED) {
            return -1;
        }
        List<LyricLine> lines = r.lyrics().lines();
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            Long start = lines.get(i).startMs();
            if (start != null && start <= pos) {
                found = i;
            }
        }
        return found;
    }

    private void buildRows(Gfx g, LyricsResult r) {
        if (r == rowsFor && list.width == rowsWidth) {
            return;
        }
        rowsFor = r;
        rowsWidth = list.width;
        List<LyricRow> out = new ArrayList<>();
        List<LyricLine> lines = r.lyrics().lines();
        boolean timed = r.lyrics().tier() != LyricsTier.UNSYNCED;
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            String text = line.text() == null || line.text().isBlank() ? "♪" : line.text();
            for (FormattedCharSequence part : g.font().split(Component.literal(text), list.width - 8)) {
                out.add(new LyricRow(i, part, timed ? line.startMs() : null));
            }
        }
        rows = out;
    }

    private void renderRow(Gfx g, LyricRow row, int index, int x, int y, int w, int h, boolean hover, long now) {
        boolean current = row.line() == followedLine && followedLine >= 0;
        int color = current ? ui.accent().accent() : row.line() < followedLine ? UiColors.TEXT_FAINT : UiColors.TEXT;
        if (hover && row.startMs() != null) {
            g.fill(x, y, x + w, y + h, UiColors.ROW_HOVER);
        }
        g.text(row.text(), x + 4, y + 1, color, false);
    }

    private void clickRow(LyricRow row, int index, int button, double mx, double my, boolean doubleClick) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && row.startMs() != null) {
            ItemActions.call(ui, () -> ui.api().seekTo(row.startMs()), v -> scrolledAt = 0);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (lyrics != null && lyrics.mouseClicked(mx, my, button)) {
            return true;
        }
        return list.mouseClicked(mx, my, button, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (list.mouseScrolled(mx, my, amount)) {
            scrolledAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}
