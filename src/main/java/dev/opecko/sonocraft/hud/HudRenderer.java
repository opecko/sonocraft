package dev.opecko.sonocraft.hud;

import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.PlaybackStatus;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.StatusReason;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.model.LyricLine;
import dev.opecko.sonocraft.api.model.LyricWord;
import dev.opecko.sonocraft.api.model.LyricsTier;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.compat.oneconfig.OneConfigIntegration;
import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.config.SonocraftConfig;
import dev.opecko.sonocraft.state.LyricsStore;
import dev.opecko.sonocraft.state.PlayerSnapshot;
import dev.opecko.sonocraft.theme.AccentPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;

/**
 * Draws the Now Playing panel (PLAN §5): artwork with crossfade, status icon, title/artist/album/time lines with
 * scrolling for long text, progress bar, background and border, all laid out by {@link HudLayout}. Fades in and
 * out instead of popping. Texts are rebuilt only when the item or the second changes. Render thread only.
 */
public final class HudRenderer {
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath(Sonocraft.MOD_ID, "textures/gui/hud_icons.png");
    private static final int WHITE = 0xFFFFFF;
    private static final int LIGHT = 0xDDDDDD;
    private static final int GRAY = 0xAAAAAA;
    private static final long FADE_MS = 250;
    private static final long ART_FADE_MS = 300;
    private static final long MARQUEE_PAUSE_MS = 1500;
    private static final int MARQUEE_GAP = 24;

    private final HudLayout layout = new HudLayout();
    private final HudLayout statusLayout = new HudLayout();
    private final ToIntFunction<String> measure = s -> Minecraft.getInstance().font.width(s);

    // texts, rebuilt when the item changes
    private MediaItem textItem;
    /** The texts are the "nothing playing" ones (not an item's, not the editor's samples). */
    private boolean nothingTexts;
    private String title;
    private String artist;
    private String album;
    // time text, rebuilt when the shown second changes
    private long timeKey = Long.MIN_VALUE;
    private String time;
    // ellipsized lines, per line: source text + width -> cut text
    private final String[] cutSource = new String[4];
    private final int[] cutWidth = new int[4];
    private final String[] cut = new String[4];
    private long marqueeStart;

    private float alpha;
    private long lastFrameNanos;
    private long pausedSinceMs;

    private ArtTextures.Art shownArt;
    private ArtTextures.Art previousArt;
    private long artChangedMs;

    /** The layout of the last drawn panel (for the HUD editor). */
    public HudLayout layout() {
        return layout;
    }

    /** HUD element entry point. */
    public void renderHud(Gfx g) {
        if (OneConfigIntegration.isEditorOpen()) {
            // OneConfig's HUD editor frames our panel: show it even when nothing plays or menus hide it
            renderPreview(g);
            return;
        }
        SonocraftCore core = SonocraftCore.get();
        HudConfig c = core.config.get().hud;
        long now = System.currentTimeMillis();
        float dt = frameSeconds();
        SessionStatus status = core.session.status();
        PlayerSnapshot p = core.player.get();

        boolean screenHides = false;
        Screen screen = Mc.screen();
        if (screen instanceof HudEditorScreen) {
            screenHides = true; // the editor draws its own preview
        } else if (c.hideInScreens && screen != null && !(screen instanceof ChatScreen)) {
            screenHides = true;
        }
        boolean base = c.enabled && !Mc.hudHidden() && !screenHides;

        if (status.state() != SessionState.CONNECTED) {
            alpha = 0;
            if (base && showStatus(c.connectionStatus, status)) {
                renderStatus(g, c, status);
            }
            return;
        }

        if (p.status() == PlaybackStatus.PLAYING) {
            pausedSinceMs = 0;
        } else if (pausedSinceMs == 0) {
            pausedSinceMs = now;
        }
        boolean nothing = p.item() == null;
        boolean visible = base
                && !(nothing && c.hideWhenNothingPlaying)
                && !(c.hideAfterPausedSeconds > 0 && pausedSinceMs != 0
                        && now - pausedSinceMs > c.hideAfterPausedSeconds * 1000L);
        float step = dt * 1000f / FADE_MS;
        alpha = visible ? Math.min(1, alpha + step) : Math.max(0, alpha - step);
        if (alpha <= 0.01f) {
            return;
        }
        updateTexts(p, c, now);
        LyricsStore.Synced lyrics = currentLyrics(c, core, p.item());
        draw(g, c, core, p.status(), p.progressAt(now), lyrics, p.positionAt(now), now, alpha);
    }

    /** Editor preview: always visible, sample texts when nothing is playing. */
    public void renderPreview(Gfx g) {
        SonocraftCore core = SonocraftCore.get();
        HudConfig c = core.config.get().hud;
        long now = System.currentTimeMillis();
        PlayerSnapshot p = core.player.get();
        boolean live = core.session.status().state() == SessionState.CONNECTED && p.item() != null;
        PlaybackStatus status;
        double progress;
        LyricsStore.Synced lyrics;
        long position;
        if (live) {
            updateTexts(p, c, now);
            status = p.status();
            progress = p.progressAt(now);
            lyrics = currentLyrics(c, core, p.item());
            if (lyrics == null && c.lyrics.enabled) {
                lyrics = sampleLyrics();
                position = now % SAMPLE_LYRICS_MS;
            } else {
                position = p.positionAt(now);
            }
        } else {
            textItem = null;
            nothingTexts = false;
            title = Component.translatable("sonocraft.hud.sample.title").getString();
            artist = Component.translatable("sonocraft.hud.sample.artist").getString();
            album = Component.translatable("sonocraft.hud.sample.album").getString();
            time = formatTime(c.time.format, 83_000, 235_000L);
            status = PlaybackStatus.PLAYING;
            progress = 83 / 235.0;
            lyrics = c.lyrics.enabled ? sampleLyrics() : null;
            position = now % SAMPLE_LYRICS_MS;
        }
        draw(g, c, core, status, progress, lyrics, position, now, 1f);
    }

    private float frameSeconds() {
        long t = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (t - lastFrameNanos) / 1e9f);
        lastFrameNanos = t;
        return dt;
    }

    private void updateTexts(PlayerSnapshot p, HudConfig c, long now) {
        MediaItem item = p.item();
        if (item == null) {
            if (!nothingTexts) {
                nothingTexts = true;
                textItem = null;
                title = Component.translatable("sonocraft.hud.nothing_playing").getString();
                artist = null;
                album = null;
                marqueeStart = now;
            }
            time = null;
            return;
        }
        if (item != textItem && !sameItem(item, textItem)) {
            marqueeStart = now;
        }
        if (item != textItem || nothingTexts) {
            nothingTexts = false;
            textItem = item;
            title = item.displayTitle();
            if (item instanceof Track t) {
                artist = t.artistNames(", ");
                album = t.album() != null ? t.album().title() : null;
            } else if (item instanceof Episode e) {
                artist = e.author();
                album = e.podcast() != null ? e.podcast().title() : null;
            } else {
                artist = null;
                album = null;
            }
        }
        long pos = p.positionAt(now);
        long key = (pos / 1000) * 31 + (p.durationMs() != null ? p.durationMs() / 1000 : -1) * 7 + c.time.format.ordinal();
        if (key != timeKey || time == null) {
            timeKey = key;
            time = formatTime(c.time.format, pos, p.durationMs());
        }
    }

    private static boolean sameItem(MediaItem a, MediaItem b) {
        return b != null && a.type() == b.type() && a.id().equals(b.id());
    }

    static String formatTime(HudConfig.TimeFormat format, long pos, Long duration) {
        if (duration == null || duration <= 0) {
            return Texts.time(pos);
        }
        return switch (format) {
            case ELAPSED_TOTAL -> Texts.time(pos) + " / " + Texts.time(duration);
            case ELAPSED_REMAINING -> Texts.time(pos) + " / -" + Texts.time(duration - pos);
            case TOTAL -> Texts.time(duration);
        };
    }

    private void draw(Gfx g, HudConfig c, SonocraftCore core, PlaybackStatus status, double progress,
                      LyricsStore.Synced lyrics, long position, long now, float alpha) {
        int lyricsRows = lyrics != null ? c.lyrics.linesBefore + 1 + c.lyrics.linesAfter : 0;
        layout.compute(c, title, artist, album, c.time.enabled ? time : null, lyricsRows, measure);
        layout.place(c, g.width(), g.height());
        AccentPalette accent = core.theme.palette(now);

        // ask for artwork at the size it is shown, in real pixels
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        core.artTracker.setSize(Math.round(c.art.size * c.scale * guiScale));

        g.push();
        g.translate(layout.screenX, layout.screenY);
        g.scale(layout.scale);

        switch (c.background) {
            case COLOR -> g.fill(0, 0, layout.width, layout.height, fade(c.backgroundColor, alpha));
            case ACCENT -> g.fill(0, 0, layout.width, layout.height, fade(accent.dim(), alpha));
            case NONE -> {
            }
        }
        if (c.border) {
            int b = fade(accent.accent(), alpha);
            g.fill(0, 0, layout.width, 1, b);
            g.fill(0, layout.height - 1, layout.width, layout.height, b);
            g.fill(0, 1, 1, layout.height - 1, b);
            g.fill(layout.width - 1, 1, layout.width, layout.height - 1, b);
        }

        if (layout.art) {
            drawArt(g, c, core, accent, now, alpha);
        }
        if (layout.icon) {
            drawIcon(g, status, accent, now, alpha);
        }
        drawLine(g, 0, layout.title, title, WHITE, c, now, alpha);
        drawLine(g, 1, layout.artist, artist, LIGHT, c, now, alpha);
        drawLine(g, 2, layout.album, album, GRAY, c, now, alpha);
        drawLine(g, 3, layout.time, time, GRAY, c, now, alpha);
        if (layout.bar) {
            g.fill(layout.barX, layout.barY, layout.barX + layout.barWidth, layout.barY + layout.barHeight,
                    fade(0x50FFFFFF, alpha));
            if (progress >= 0) {
                int filled = (int) Math.round(layout.barWidth * Math.min(1, progress));
                g.fill(layout.barX, layout.barY, layout.barX + filled, layout.barY + layout.barHeight,
                        fade(accent.accent(), alpha));
            }
        }
        if (layout.lyricsRows > 0) {
            drawLyrics(g, c, core, lyrics, position, accent, now, alpha);
        }
        g.pop();
    }

    private void drawArt(Gfx g, HudConfig c, SonocraftCore core, AccentPalette accent, long now, float alpha) {
        int x = layout.artX;
        int y = layout.artY;
        int s = layout.artSize;
        ArtTextures.Art art = core.artTracker.current();
        if (art != shownArt) {
            previousArt = shownArt;
            shownArt = art;
            artChangedMs = now;
        }
        if (c.art.shadow) {
            g.fill(x + 1, y + 1, x + s + 1, y + s + 1, fade(0x60000000, alpha));
        }
        float t = Math.min(1, (now - artChangedMs) / (float) ART_FADE_MS);
        if (previousArt != null && t < 1) {
            blitArt(g, core, previousArt, c.art.rounding, x, y, s, alpha * (1 - t));
        } else if (shownArt == null) {
            g.fill(x, y, x + s, y + s, fade(accent.dim(), alpha));
        }
        if (shownArt != null) {
            blitArt(g, core, shownArt, c.art.rounding, x, y, s, alpha * t);
        }
    }

    private static void blitArt(Gfx g, SonocraftCore core, ArtTextures.Art art, int roundingPct, int x, int y, int s,
                                float alpha) {
        int radius = Math.round(art.size() * roundingPct / 100f);
        Identifier id = core.art.rounded(art, radius);
        g.blit(id, x, y, s, s, 0, 0, art.size(), art.size(), art.size(), art.size(), fade(0xFFFFFFFF, alpha));
    }

    private void drawIcon(Gfx g, PlaybackStatus status, AccentPalette accent, long now, float alpha) {
        int x = layout.iconX;
        int y = layout.iconY;
        int s = layout.iconSize;
        int color = fade(accent.accent(), alpha);
        if (status == PlaybackStatus.BUFFERING) {
            // three dots lighting up in turn
            int dot = Math.max(1, s / 4);
            int lit = (int) (now / 250 % 3);
            for (int i = 0; i < 3; i++) {
                int dx = x + i * (s - dot) / 2;
                int dy = y + (s - dot) / 2;
                g.fill(dx, dy, dx + dot, dy + dot, i == lit ? color : fade(accent.accent(), alpha * 0.35f));
            }
            return;
        }
        int u = switch (status) {
            case PLAYING -> 0;
            case PAUSED -> 16;
            default -> 32;
        };
        g.blit(ICONS, x, y, s, s, u, 0, 16, 16, 48, 16, color);
    }

    private void drawLine(Gfx g, int index, HudLayout.Line l, String text, int rgb, HudConfig c, long now,
                          float alpha) {
        if (!l.visible || text == null || l.width <= 0) {
            return;
        }
        int color = fade(0xFF000000 | rgb, alpha);
        if (!l.overflows()) {
            drawScaled(g, text, l.x, l.y, l.scale, 0, color);
            return;
        }
        if (c.marqueeSpeed <= 0) {
            drawScaled(g, ellipsize(g, index, text, Math.round(l.width / l.scale)), l.x, l.y, l.scale, 0, color);
            return;
        }
        float loop = l.textWidth + MARQUEE_GAP * l.scale;
        long cycleMs = MARQUEE_PAUSE_MS + Math.round(loop * 1000 / c.marqueeSpeed);
        long t = (now - marqueeStart) % cycleMs;
        float scroll = t < MARQUEE_PAUSE_MS ? 0 : (t - MARQUEE_PAUSE_MS) * c.marqueeSpeed / 1000f;
        g.scissor(l.x, l.y, l.x + l.width, l.y + l.height + 1);
        drawScaled(g, text, l.x, l.y, l.scale, -scroll, color);
        drawScaled(g, text, l.x, l.y, l.scale, loop - scroll, color);
        g.endScissor();
    }

    private static void drawScaled(Gfx g, String text, int x, int y, float scale, float dx, int color) {
        if (scale == 1f) {
            g.push();
            g.translate(x + dx, y);
            g.text(text, 0, 0, color, true);
            g.pop();
            return;
        }
        g.push();
        g.translate(x + dx, y);
        g.scale(scale);
        g.text(text, 0, 0, color, true);
        g.pop();
    }

    private String ellipsize(Gfx g, int index, String text, int width) {
        if (text.equals(cutSource[index]) && cutWidth[index] == width) {
            return cut[index];
        }
        String dots = "…";
        String head = g.font().plainSubstrByWidth(text, Math.max(0, width - g.textWidth(dots)));
        cutSource[index] = text;
        cutWidth[index] = width;
        cut[index] = head + dots;
        return cut[index];
    }

    /** Synced lyrics of the current track when the HUD should show them, else null. */
    private static LyricsStore.Synced currentLyrics(HudConfig c, SonocraftCore core, MediaItem item) {
        if (!c.lyrics.enabled || !(item instanceof Track) || !core.session.status().has(Scope.LYRICS_READ)) {
            return null;
        }
        LyricsStore.Synced s = core.lyrics.get();
        return !s.isEmpty() && item.id().equals(s.trackId()) ? s : null;
    }

    private static final long SAMPLE_LYRICS_MS = 9000;
    private LyricsStore.Synced sample;

    /** Three made-up lines with word timings, looping every {@link #SAMPLE_LYRICS_MS}, for the editor preview. */
    private LyricsStore.Synced sampleLyrics() {
        if (sample == null) {
            List<LyricLine> lines = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String text = Component.translatable("sonocraft.hud.sample.lyrics." + (i + 1)).getString();
                String[] parts = text.split("(?<= )");
                long start = i * 3000L;
                long step = 2600 / Math.max(1, parts.length);
                List<LyricWord> words = new ArrayList<>();
                for (int w = 0; w < parts.length; w++) {
                    words.add(new LyricWord(start + w * step, start + (w + 1) * step, parts[w]));
                }
                lines.add(new LyricLine(start, start + 2900, text, words, List.of()));
            }
            sample = new LyricsStore.Synced("sample", LyricsTier.WORD, lines);
        }
        return sample;
    }

    // lyrics scroll state
    private int shownLine = Integer.MIN_VALUE;
    private int previousLine;
    private long lineChangedMs;
    // pixel ends of the words of the current line, at scale 1
    private LyricLine measuredLine;
    private float[] wordEnds = new float[0];

    private void drawLyrics(Gfx g, HudConfig c, SonocraftCore core, LyricsStore.Synced lyrics, long position,
                            AccentPalette accent, long now, float alpha) {
        List<LyricLine> lines = lyrics.lines();
        int current = lyrics == sample ? LyricsStore.lineAt(lines, position)
                : core.lyrics.currentLine(lyrics, position);
        if (current != shownLine) {
            previousLine = shownLine;
            shownLine = current;
            lineChangedMs = now;
        }
        int before = c.lyrics.linesBefore;
        int after = c.lyrics.linesAfter;
        int rowH = layout.lyricsRowHeight;
        int x0 = layout.lyricsX;
        int y0 = layout.lyricsY;
        int w = layout.lyricsWidth;
        float scale = c.lyrics.scale;

        // slide up by one row when the next line starts; bigger jumps (seeking) just switch
        float shift = 0;
        if (c.lyrics.smooth && current - previousLine == 1) {
            float t = Math.min(1, (now - lineChangedMs) / 200f);
            shift = (1 - t) * rowH;
        }
        g.scissor(x0, y0, x0 + w, y0 + layout.lyricsRows * rowH);
        for (int r = -before - 1; r <= after + 1; r++) {
            int index = current + r;
            if (index < 0 || index >= lines.size()) {
                continue;
            }
            LyricLine line = lines.get(index);
            String text = line.text() == null || line.text().isBlank() ? "♪" : line.text();
            int y = Math.round(y0 + (r + before) * rowH + shift);
            int textW = Math.round(measure.applyAsInt(text) * scale);
            if (r == 0) {
                Long nextStart = index + 1 < lines.size() ? lines.get(index + 1).startMs() : null;
                drawSungLine(g, c, line, nextStart, text, textW, x0, y, w, position, accent, alpha,
                        lyrics.wordTimed());
            } else {
                float dim = Math.abs(r) == 1 ? 0.55f : 0.35f;
                int x = textW > w ? x0 : x0 + alignOffset(c.textAlign, w - textW);
                drawScaled(g, text, x, y, scale, 0, fade(0xFFFFFFFF, alpha * dim));
            }
        }
        g.endScissor();
    }

    /** The line being sung: dim, with the sung part in the accent colour; long lines follow the sung word. */
    private void drawSungLine(Gfx g, HudConfig c, LyricLine line, Long nextStart, String text, int textW, int x0,
                              int y, int w, long position, AccentPalette accent, float alpha, boolean wordTimed) {
        float scale = c.lyrics.scale;
        float filled;
        boolean byWord = wordTimed && c.lyrics.highlightWords && line.words() != null && !line.words().isEmpty();
        if (byWord) {
            measureWords(line);
            double sung = LyricsStore.sungWords(line, position);
            int done = (int) Math.min(sung, wordEnds.length);
            float start = done == 0 ? 0 : wordEnds[done - 1];
            float end = done < wordEnds.length ? wordEnds[done] : start;
            filled = (start + (float) (sung - done) * (end - start)) * scale;
        } else {
            filled = textW;
        }
        int x = x0 + alignOffset(c.textAlign, w - textW);
        if (textW > w) {
            float scroll;
            if (byWord) {
                // keep the sung word about a third into the box
                scroll = Math.max(0, Math.min(textW - w, filled - w / 3f));
            } else {
                // no word timings: move through the line over its time, holding a moment at both ends to read them
                double t = LyricsStore.lineProgress(line, nextStart, position);
                double moving = Math.max(0, Math.min(1, (t - 0.15) / 0.7));
                scroll = (float) (moving * (textW - w));
            }
            x = Math.round(x0 - scroll);
        }
        if (filled < textW) {
            drawScaled(g, text, x, y, scale, 0, fade(0xFFFFFFFF, alpha * 0.6f));
        }
        if (filled > 0) {
            g.scissor(x, y - 1, x + Math.round(filled), y + layout.lyricsRowHeight);
            // line-timed lyrics show the whole line in white; word-timed ones keep the accent to the end
            drawScaled(g, text, x, y, scale, 0, fade(byWord ? accent.accent() : 0xFFFFFFFF, alpha));
            g.endScissor();
        }
    }

    /** Cumulative pixel widths of the line's words at scale 1, measured once per line. */
    private void measureWords(LyricLine line) {
        if (line == measuredLine) {
            return;
        }
        measuredLine = line;
        List<LyricWord> words = line.words();
        wordEnds = new float[words.size()];
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            prefix.append(words.get(i).text());
            wordEnds[i] = measure.applyAsInt(prefix.toString());
        }
    }

    private static int alignOffset(HudConfig.Align a, int free) {
        if (free <= 0) {
            return 0;
        }
        return switch (a) {
            case LEFT -> 0;
            case CENTER -> free / 2;
            case RIGHT -> free;
        };
    }

    private static boolean showStatus(SonocraftConfig.StatusVisibility visibility, SessionStatus status) {
        return switch (visibility) {
            case NEVER -> false;
            case ALWAYS -> true;
            // a fresh install that was never connected and the short connecting step are not problems
            case ON_ERROR -> !(status.reason() == StatusReason.NONE
                    && (status.state() == SessionState.NOT_CONNECTED || status.state() == SessionState.CONNECTING));
        };
    }

    /** The connection state in a small box where the panel would be. */
    private void renderStatus(Gfx g, HudConfig c, SessionStatus status) {
        Component text = Component.literal(Sonocraft.NAME + ": ").append(Texts.status(status));
        int p = 3;
        int maxWidth = 220;
        var lines = g.font().split(text, maxWidth);
        int w = 0;
        for (var line : lines) {
            w = Math.max(w, g.font().width(line));
        }
        statusLayout.width = w + 2 * p;
        statusLayout.height = lines.size() * 10 + 2 * p - 1;
        statusLayout.place(c, g.width(), g.height());
        g.push();
        g.translate(statusLayout.screenX, statusLayout.screenY);
        g.scale(statusLayout.scale);
        g.fill(0, 0, statusLayout.width, statusLayout.height, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.text(lines.get(i), p, p + i * 10, 0xFFAAAAAA, true);
        }
        g.pop();
    }

    /** Multiplies an ARGB colour's alpha. */
    static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
