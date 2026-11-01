package dev.opecko.sonocraft.ui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.ErrorCode;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.api.model.Album;
import dev.opecko.sonocraft.api.model.Artist;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.HistoryEntry;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.PlaybackStatus;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.Podcast;
import dev.opecko.sonocraft.api.model.Rating;
import dev.opecko.sonocraft.api.model.Ref;
import dev.opecko.sonocraft.api.model.RepeatMode;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.api.session.SessionListener;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.config.ConfigScreen;
import dev.opecko.sonocraft.state.PlayerSnapshot;
import dev.opecko.sonocraft.theme.AccentPalette;
import dev.opecko.sonocraft.ui.page.ListPage;
import dev.opecko.sonocraft.ui.page.NowPlayingPage;
import dev.opecko.sonocraft.ui.page.Page;
import dev.opecko.sonocraft.ui.page.QueuePage;
import dev.opecko.sonocraft.ui.page.SearchPage;
import dev.opecko.sonocraft.ui.widget.ContextMenu;
import dev.opecko.sonocraft.ui.widget.IconButton;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import dev.opecko.sonocraft.ui.widget.Slider;
import dev.opecko.sonocraft.ui.widget.TextButton;
import dev.opecko.sonocraft.ui.widget.Widget;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Library screen (PLAN §6): a sidebar of sections, the current page with Back navigation, and a mini player.
 * Works with the mouse (right click = context menu) and the keyboard (Esc, Backspace = back, Ctrl+F = search,
 * arrows and Enter in lists).
 */
public final class LibraryScreen extends Screen implements UiContext {
    private static final int SIDEBAR_MIN = 104;
    private static final int SIDEBAR_MAX = 140;
    private static final int PLAYER = 42;
    private static final int TOP_BAR = 18;
    private static final int TAB_ROW = 14;

    /** Sidebar width: fits the longest label (they differ by language), within SIDEBAR_MIN..SIDEBAR_MAX. */
    private int sidebar = SIDEBAR_MIN;

    enum Tab {
        NOW_PLAYING(Icon.PLAY), QUEUE(Icon.QUEUE), SEARCH(Icon.SEARCH),
        PLAYLISTS(Icon.MUSIC), LIKED(Icon.HEART), ALBUMS(null), ARTISTS(null), PODCASTS(null), FOR_LATER(null), HISTORY(null);

        final Icon icon;

        Tab(Icon icon) {
            this.icon = icon;
        }

        boolean library() {
            return ordinal() >= PLAYLISTS.ordinal();
        }
    }

    /** The tab shown when the screen opens again. */
    private static Tab lastTab = Tab.NOW_PLAYING;

    private final Screen parent;
    private final Map<Tab, Page> roots = new EnumMap<>(Tab.class);
    private final ArrayDeque<Page> stack = new ArrayDeque<>();
    private Tab tab;
    private final List<EditBox> fields = new ArrayList<>();
    private final Map<String, Rating> ratings = new HashMap<>();
    private final SessionListener listener;

    private int winX;
    private int winY;
    private int winW;
    private int winH;

    private ContextMenu menu;
    private double menuX;
    private double menuY;
    private Dialog dialog;
    private Component banner;
    private Component bannerAction;
    private Runnable bannerRun;
    private long bannerAt;

    // mini player
    private final IconButton prev = new IconButton(Icon.PREVIOUS, () -> call(api()::previous));
    private final IconButton playPause = new IconButton(
            () -> player().isPlaying() ? Icon.PAUSE : Icon.PLAY_FULL, () -> accent().onAccent(), () -> call(api()::toggle))
            .filled(() -> accent().accent());
    private final IconButton next = new IconButton(Icon.NEXT, () -> call(api()::next));
    private final IconButton shuffle = new IconButton(() -> Icon.SHUFFLE,
            () -> player().shuffle() ? accent().accent() : UiColors.TEXT_DIM, () -> call(() -> api().setShuffle(!player().shuffle())));
    private final IconButton repeat = new IconButton(() -> player().repeat() == RepeatMode.ONE ? Icon.REPEAT_ONE : Icon.REPEAT,
            () -> player().repeat() != RepeatMode.OFF ? accent().accent() : UiColors.TEXT_DIM, this::cycleRepeat);
    private final IconButton like = new IconButton(() -> rating() == Rating.LIKE ? Icon.HEART_FULL : Icon.HEART,
            () -> rating() == Rating.LIKE ? accent().accent() : UiColors.TEXT_DIM, this::toggleLike);
    private final IconButton mute = new IconButton(() -> player().muted() ? Icon.MUTE : Icon.VOLUME,
            () -> UiColors.TEXT_DIM, () -> call(() -> api().setVolume(null, !player().muted())));
    private final Slider seek = new Slider(() -> player().progressAt(System.currentTimeMillis()), () -> accent().accent(),
            v -> {
                Long d = player().durationMs();
                if (d != null) {
                    call(() -> api().seekTo(Math.round(v * d)));
                }
            });
    private final Slider volume = new Slider(() -> player().volume() / 100.0, () -> accent().accent(),
            v -> call(() -> api().setVolume((int) Math.round(v * 100), null))).live();
    private final IconButton back = new IconButton(Icon.BACK, this::back);
    private final List<Widget> playerWidgets = List.of(prev, playPause, next, shuffle, repeat, like, mute, seek, volume);
    private final TextButton connection = new TextButton(Component.translatable("sonocraft.ui.connection"),
            () -> accent().accent(), true, () -> Mc.setScreen(new ConnectionScreen(this)));

    public LibraryScreen(Screen parent) {
        super(Component.translatable("sonocraft.ui.title"));
        this.parent = parent;
        this.listener = new SessionListener() {
            @Override
            public void onEvent(String method, JsonObject params) {
                Minecraft.getInstance().execute(() -> {
                    Page page = stack.peek();
                    if (page != null) {
                        page.onEvent(method, params);
                    }
                    if (method.equals("player.trackChanged")) {
                        ratings.clear();
                    }
                });
            }
        };
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        int label = font.width(Component.translatable("sonocraft.ui.settings"));
        for (Tab t : Tab.values()) {
            label = Math.max(label, font.width(tabLabel(t)) + (t == Tab.PLAYLISTS ? 16 : 0));
        }
        for (SessionState s : SessionState.values()) {
            label = Math.max(label, font.width(Component.translatable(
                    "sonocraft.ui.state." + s.name().toLowerCase(Locale.ROOT))));
        }
        sidebar = Math.clamp(label + 21 + 6, SIDEBAR_MIN, SIDEBAR_MAX);
        winW = Math.min(width - 20, 560);
        winH = Math.min(height - 20, 340);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        for (EditBox f : fields) {
            addRenderableWidget(f);
        }
        if (tab == null) {
            SonocraftCore.get().session.addListener(listener);
            select(lastTab);
        } else {
            layoutPage();
        }
    }

    @Override
    public void removed() {
        SonocraftCore.get().session.removeListener(listener);
        Page page = stack.peek();
        if (page != null) {
            page.onHide();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return SonocraftCore.get().config.get().library.pauseGame;
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    private void select(Tab t) {
        Page current = stack.peek();
        if (current != null) {
            current.onHide();
        }
        stack.clear();
        tab = t;
        lastTab = t;
        Page root = roots.computeIfAbsent(t, this::createRoot);
        stack.push(root);
        layoutPage();
        root.onShow();
    }

    private Page createRoot(Tab t) {
        var api = api();
        return switch (t) {
            case NOW_PLAYING -> new NowPlayingPage(this);
            case QUEUE -> new QueuePage(this);
            case SEARCH -> new SearchPage(this);
            case PLAYLISTS -> {
                ListPage<Playlist> page = new ListPage<>(this, tabLabel(t), c -> api.libraryPlaylists(c, null), p -> p, null);
                yield page.withAction(Component.translatable("sonocraft.ui.playlist.new"), () -> askText(
                        Component.translatable("sonocraft.ui.playlist.new"), "", title -> ItemActions.call(this,
                                () -> api.playlistCreate(title, null, null, null), r -> {
                                    notify(Component.translatable("sonocraft.ui.done.created"));
                                    libraryChanged();
                                    // YouTube can take a moment to list a new playlist
                                    page.addFirst(r, p -> p.id());
                                })));
            }
            case LIKED -> new ListPage<MediaItem>(this, tabLabel(t), c -> api.libraryLikedSongs(c, null), i -> i, null);
            case ALBUMS -> new ListPage<Album>(this, tabLabel(t), c -> api.libraryAlbums(c, null), i -> i, null);
            case ARTISTS -> new ListPage<Artist>(this, tabLabel(t), c -> api.libraryArtists(c, null), i -> i, null);
            case PODCASTS -> new ListPage<Podcast>(this, tabLabel(t), c -> api.libraryPodcasts(c, null), i -> i, null);
            case FOR_LATER -> new ListPage<Episode>(this, tabLabel(t), c -> api.libraryEpisodesForLater(c, null), i -> i, null);
            case HISTORY -> new ListPage<HistoryEntry>(this, tabLabel(t), c -> api.libraryHistory(c, null),
                    HistoryEntry::item, HistoryEntry::sectionText);
        };
    }

    private static Component tabLabel(Tab t) {
        return Component.translatable("sonocraft.ui.tab." + t.name().toLowerCase(Locale.ROOT));
    }

    private void layoutPage() {
        Page page = stack.peek();
        if (page != null) {
            page.layout(winX + sidebar, winY + TOP_BAR, winW - sidebar, winH - PLAYER - TOP_BAR);
        }
    }

    // ------------------------------------------------------------------ UiContext

    @Override
    public AccentPalette accent() {
        return SonocraftCore.get().theme.palette(System.currentTimeMillis());
    }

    @Override
    public void open(Page page) {
        Page current = stack.peek();
        if (current != null) {
            current.onHide();
        }
        stack.push(page);
        layoutPage();
        page.onShow();
    }

    @Override
    public void back() {
        if (stack.size() <= 1) {
            return;
        }
        stack.pop().onHide();
        layoutPage();
        stack.peek().onShow();
    }

    @Override
    public void notify(Component message) {
        showBanner(message, null, null);
    }

    private void showBanner(Component message, Component action, Runnable run) {
        banner = message;
        bannerAction = action;
        bannerRun = run;
        bannerAt = System.currentTimeMillis();
    }

    @Override
    public void error(Throwable error) {
        if (error instanceof SonolithException se && se.is(ErrorCode.PERMISSION_DENIED) && se.scope() != null) {
            Scope scope = se.scope();
            showBanner(Loader.describe(error), Component.translatable("sonocraft.ui.allow"),
                    () -> SonocraftCore.get().session.requestScopes(List.of(scope)));
        } else {
            showBanner(Loader.describe(error), null, null);
        }
    }

    @Override
    public void openMenu(ContextMenu m, double mx, double my) {
        if (m.isEmpty()) {
            return;
        }
        menu = m;
        menuX = mx;
        menuY = my;
        m.onClose(() -> menu = null);
    }

    @Override
    public void confirm(Component title, Component message, Runnable onYes) {
        openDialog(new ConfirmDialog(title, message, onYes));
    }

    @Override
    public void askText(Component title, String initial, Consumer<String> onDone) {
        openDialog(new TextDialog(title, initial, onDone));
    }

    @Override
    public void addToPlaylist(List<Ref> items) {
        openDialog(new PlaylistDialog(items));
    }

    @Override
    public void libraryChanged() {
        if (sidebarPlaylists != null) {
            sidebarPlaylists.reload();
        }
        if (roots.get(Tab.PLAYLISTS) instanceof ListPage<?> playlists) {
            playlists.reload();
        }
    }

    @Override
    public void addField(EditBox field) {
        if (!fields.contains(field)) {
            fields.add(field);
            addRenderableWidget(field);
        }
        field.visible = dialog == null;
    }

    @Override
    public void removeField(EditBox field) {
        if (field != null) {
            fields.remove(field);
            removeWidget(field);
        }
    }

    @Override
    public void focus(EditBox field) {
        setFocused(field);
    }

    @Override
    public ArtTextures.Art thumbnail(String url, int guiSize) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        int px = Math.max(32, (guiSize * scale + 31) / 32 * 32);
        return SonocraftCore.get().art.get(url, px);
    }

    // ------------------------------------------------------------------ mini player helpers

    private static PlayerSnapshot player() {
        return SonocraftCore.get().player.get();
    }

    private void call(java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> action) {
        ItemActions.call(this, action, v -> { });
    }

    private void cycleRepeat() {
        RepeatMode nextMode = switch (player().repeat()) {
            case OFF -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.OFF;
        };
        call(() -> api().setRepeat(nextMode));
    }

    /** The current track's rating; a like given here shows at once, before Sonolith reports it. */
    private Rating rating() {
        MediaItem item = player().item();
        if (item == null) {
            return null;
        }
        Rating local = ratings.get(item.id());
        return local != null ? local : item instanceof Track t ? t.rating() : null;
    }

    private void toggleLike() {
        MediaItem item = player().item();
        if (item == null) {
            return;
        }
        Rating to = rating() == Rating.LIKE ? Rating.NONE : Rating.LIKE;
        ItemActions.call(this, () -> api().ratingSet(item.ref(), to), v -> ratings.put(item.id(), to));
    }

    // ------------------------------------------------------------------ rendering

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Gfx gfx = new Gfx(g);
        draw(gfx, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawOverlays(gfx, mouseX, mouseY);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) {
            super.extractBackground(g, mouseX, mouseY, delta);
        } else {
            g.fill(0, 0, width, height, 0x60000000);
        }
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        Gfx gfx = new Gfx(g);
        draw(gfx, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
        drawOverlays(gfx, mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) {
            super.renderBackground(g, mouseX, mouseY, delta);
        } else {
            g.fill(0, 0, width, height, 0x60000000);
        }
    }
    *///?}

    private boolean overlayOpen() {
        return menu != null || dialog != null;
    }

    private void draw(Gfx g, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        // pages and widgets don't react to hover under an open menu or dialog
        int mx = overlayOpen() ? -1 : mouseX;
        int my = overlayOpen() ? -1 : mouseY;
        g.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, UiColors.DIVIDER);
        g.fill(winX, winY, winX + winW, winY + winH, UiColors.WINDOW);
        drawSidebar(g, mx, my);

        SessionStatus status = SonocraftCore.get().session.status();
        int cx = winX + sidebar;
        int cw = winW - sidebar;
        Page page = stack.peek();
        if (status.state() != SessionState.CONNECTED) {
            int cy = winY + (winH - PLAYER) / 2 - 20;
            int used = g.wrappedText(Texts.status(status), cx + 20, cy, cw - 40, UiColors.TEXT_DIM);
            int bw = connection.preferredWidth(g);
            connection.setBounds(cx + (cw - bw) / 2, cy + used + 8, bw, 18);
            connection.render(g, mx, my, now);
        } else if (page != null) {
            // top bar: back and title
            back.visible = stack.size() > 1;
            back.setBounds(cx + 2, winY + 2, 14, 14);
            back.render(g, mx, my, now);
            int tx = cx + (back.visible ? 20 : 6);
            g.scissor(tx, winY, winX + winW - 4, winY + TOP_BAR);
            g.text(page.title(), tx, winY + 5, UiColors.TEXT, false);
            g.endScissor();
            g.scissor(cx, winY + TOP_BAR, winX + winW, winY + winH - PLAYER);
            page.render(g, mx, my, now);
            g.endScissor();
        }
        drawPlayer(g, mx, my, now);
        drawBanner(g, mx, my, now);
        if (dialog != null) {
            // before the vanilla widgets, so a dialog's text field is drawn on top of its box
            dialog.renderOver(g, mouseX, mouseY, now);
        }
    }

    /** A sidebar row: a section, the LIBRARY heading, or one of the user's playlists under Playlists. */
    private record SideRow(Tab tab, Playlist playlist, boolean heading) {
    }

    private static boolean playlistsOpen;
    private Loader<List<Playlist>> sidebarPlaylists;
    private double sidebarScroll;
    private final List<SideRow> sideRows = new ArrayList<>();

    private int rowHeight(SideRow r) {
        return r.heading() ? 16 : TAB_ROW;
    }

    private List<SideRow> sideRows() {
        sideRows.clear();
        for (Tab t : Tab.values()) {
            if (t == Tab.PLAYLISTS) {
                sideRows.add(new SideRow(null, null, true));
            }
            sideRows.add(new SideRow(t, null, false));
            if (t == Tab.PLAYLISTS && playlistsOpen && sidebarPlaylists != null
                    && sidebarPlaylists.state() == Loader.State.READY) {
                for (Playlist p : sidebarPlaylists.value()) {
                    sideRows.add(new SideRow(null, p, false));
                }
            }
        }
        return sideRows;
    }

    private int sideTop() {
        return winY + 4;
    }

    /** Bottom of the scrolling part; Settings and the connection sit below it. */
    private int sideBottom() {
        return winY + winH - PLAYER - 2 * TAB_ROW - 4;
    }

    private void togglePlaylists() {
        playlistsOpen = !playlistsOpen;
        if (playlistsOpen && sidebarPlaylists == null) {
            sidebarPlaylists = new Loader<>(() -> api().libraryPlaylists(null, 100).thenApply(p ->
                    p.items().stream().filter(java.util.Objects::nonNull).toList()));
            sidebarPlaylists.reload();
        }
    }

    private boolean isOpen(Playlist p) {
        return stack.peek() instanceof dev.opecko.sonocraft.ui.page.PlaylistPage page && page.playlistId().equals(p.id());
    }

    private void drawSidebar(Gfx g, int mouseX, int mouseY) {
        g.fill(winX, winY, winX + sidebar, winY + winH - PLAYER, UiColors.SIDEBAR);
        List<SideRow> rows = sideRows();
        int total = 0;
        for (SideRow r : rows) {
            total += rowHeight(r);
        }
        int area = sideBottom() - sideTop();
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, total - area)));
        g.scissor(winX, sideTop(), winX + sidebar, sideBottom());
        int y = sideTop() - (int) sidebarScroll;
        for (SideRow r : rows) {
            int h = rowHeight(r);
            boolean hover = mouseX >= winX && mouseX < winX + sidebar && mouseY >= Math.max(y, sideTop())
                    && mouseY < Math.min(y + h, sideBottom());
            if (r.heading()) {
                g.text(Component.translatable("sonocraft.ui.library"), winX + 8, y + 6, UiColors.TEXT_FAINT, false);
            } else if (r.playlist() != null) {
                boolean selected = isOpen(r.playlist());
                if (selected || hover) {
                    g.fill(winX, y, winX + sidebar, y + h, selected ? 0x30FFFFFF : UiColors.ROW_HOVER);
                }
                g.scissor(winX + 20, y, winX + sidebar - 4, y + h);
                g.text(r.playlist().title(), winX + 20, y + 3, selected ? UiColors.TEXT : UiColors.TEXT_DIM, false);
                g.endScissor();
            } else {
                Tab t = r.tab();
                boolean selected = t == tab && !(stack.peek() instanceof dev.opecko.sonocraft.ui.page.PlaylistPage);
                if (selected || hover) {
                    g.fill(winX, y, winX + sidebar, y + h, selected ? 0x30FFFFFF : UiColors.ROW_HOVER);
                }
                if (selected) {
                    g.fill(winX, y, winX + 2, y + h, accent().accent());
                }
                if (t.icon != null) {
                    t.icon.draw(g, winX + 6, y + 1, selected ? accent().accent() : UiColors.TEXT_DIM);
                }
                g.text(tabLabel(t), winX + 21, y + 3, selected ? UiColors.TEXT : UiColors.TEXT_DIM, false);
                if (t == Tab.PLAYLISTS) {
                    boolean hoverArrow = hover && mouseX >= winX + sidebar - 16;
                    (playlistsOpen ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT).draw(g, winX + sidebar - 15, y + 1,
                            hoverArrow ? UiColors.TEXT : UiColors.TEXT_FAINT);
                }
            }
            y += h;
        }
        if (playlistsOpen && sidebarPlaylists != null && sidebarPlaylists.state() == Loader.State.LOADING) {
            g.text(Component.translatable("sonocraft.ui.loading"), winX + 20, y + 3, UiColors.TEXT_FAINT, false);
        }
        g.endScissor();

        // settings and the connection state at the bottom
        int by = sideBottom() + 4;
        g.fill(winX + 6, by - 3, winX + sidebar - 6, by - 2, UiColors.DIVIDER);
        boolean hoverSettings = mouseX >= winX && mouseX < winX + sidebar && mouseY >= by && mouseY < by + TAB_ROW;
        if (hoverSettings) {
            g.fill(winX, by, winX + sidebar, by + TAB_ROW, UiColors.ROW_HOVER);
        }
        Icon.SETTINGS.draw(g, winX + 6, by + 1, UiColors.TEXT_DIM);
        g.text(Component.translatable("sonocraft.ui.settings"), winX + 21, by + 3, UiColors.TEXT_DIM, false);
        by += TAB_ROW;
        SessionStatus st = SonocraftCore.get().session.status();
        boolean connected = st.state() == SessionState.CONNECTED;
        boolean hoverConn = mouseX >= winX && mouseX < winX + sidebar && mouseY >= by && mouseY < by + TAB_ROW;
        if (hoverConn) {
            g.fill(winX, by, winX + sidebar, by + TAB_ROW, UiColors.ROW_HOVER);
        }
        int dot = connected ? 0xFF55DD55 : st.state() == SessionState.CONNECTING ? 0xFFDDDD55 : 0xFFDD5555;
        g.fill(winX + 10, by + 5, winX + 14, by + 9, dot);
        g.scissor(winX + 21, by, winX + sidebar - 2, by + TAB_ROW);
        g.text(Component.translatable("sonocraft.ui.state." + st.state().name().toLowerCase(Locale.ROOT)), winX + 21,
                by + 3, hoverConn ? UiColors.TEXT : UiColors.TEXT_DIM, false);
        g.endScissor();
    }

    private void drawPlayer(Gfx g, int mouseX, int mouseY, long now) {
        int px = winX;
        int py = winY + winH - PLAYER;
        g.fill(px, py, px + winW, py + PLAYER, UiColors.PLAYER);
        g.fill(px, py, px + winW, py + 1, UiColors.DIVIDER);
        PlayerSnapshot p = player();
        MediaItem item = p.item();
        boolean connected = SonocraftCore.get().session.status().state() == SessionState.CONNECTED;
        for (Widget w : playerWidgets) {
            w.visible = connected && item != null;
        }
        if (!connected || item == null) {
            Component none = Component.translatable("sonocraft.hud.nothing_playing");
            g.text(none, px + 10, py + 17, UiColors.TEXT_FAINT, false);
            return;
        }
        // artwork and titles
        ArtTextures.Art art = SonocraftCore.get().artTracker.current();
        if (art != null) {
            g.blit(SonocraftCore.get().art.rounded(art, art.size() / 10), px + 5, py + 5, 32, 32, 0, 0, art.size(),
                    art.size(), art.size(), art.size(), 0xFFFFFFFF);
        } else {
            g.fill(px + 5, py + 5, px + 37, py + 37, accent().dim());
        }
        // transport and seek bar sit in the middle of the window, like in music apps
        int mid = px + winW / 2;
        int seekW = Math.min(winW * 2 / 5, 260);
        int seekLeft = mid - seekW / 2;
        String elapsed = Texts.time(seek.isDragging() && p.durationMs() != null
                ? Math.round(seek.shown() * p.durationMs()) : p.positionAt(now));
        String total = p.durationMs() != null ? Texts.time(p.durationMs()) : "";
        int ew = g.textWidth(elapsed);
        int tw = g.textWidth(total);
        int left = seekLeft - ew - 10 - px;
        g.scissor(px + 42, py, px + left, py + PLAYER);
        g.text(item.displayTitle(), px + 42, py + 10, UiColors.TEXT, false);
        g.text(ItemRow.subtitle(item), px + 42, py + 22, UiColors.TEXT_DIM, false);
        g.endScissor();

        // right side: like, shuffle, repeat, volume
        int right = px + winW - 6;
        volume.setBounds(right - 48, py + 16, 48, 10);
        mute.setBounds(right - 66, py + 12, 16, 16);
        repeat.setBounds(right - 86, py + 12, 16, 16);
        shuffle.setBounds(right - 104, py + 12, 16, 16);
        like.setBounds(right - 122, py + 12, 16, 16);
        boolean canRate = SonocraftCore.get().session.status().has(Scope.LIBRARY_WRITE);
        like.active = canRate;

        // play in the middle, previous and next at equal distances
        playPause.setBounds(mid - 9, py + 4, 18, 18);
        prev.setBounds(mid - 33, py + 5, 16, 16);
        next.setBounds(mid + 17, py + 5, 16, 16);
        seek.setBounds(seekLeft, py + 28, seekW, 8);
        seek.active = p.durationMs() != null && p.status() != PlaybackStatus.STOPPED;
        g.text(elapsed, seekLeft - ew - 6, py + 28, UiColors.TEXT_FAINT, false);
        g.text(total, seekLeft + seekW + 6, py + 28, UiColors.TEXT_FAINT, false);
        for (Widget w : playerWidgets) {
            w.render(g, mouseX, mouseY, now);
        }
    }

    private void drawBanner(Gfx g, int mouseX, int mouseY, long now) {
        if (banner == null) {
            return;
        }
        long age = now - bannerAt;
        long life = bannerAction != null ? 8000 : 3500;
        if (age > life) {
            banner = null;
            return;
        }
        int cx = winX + sidebar + 6;
        int cw = winW - sidebar - 12;
        int y = winY + winH - PLAYER - 22;
        int actionW = bannerAction != null ? g.textWidth(bannerAction) + 12 : 0;
        g.fill(cx, y, cx + cw, y + 18, 0xF0303030);
        g.fill(cx, y, cx + 2, y + 18, accent().accent());
        g.scissor(cx, y, cx + cw - actionW - 4, y + 18);
        g.text(banner, cx + 8, y + 5, UiColors.TEXT, false);
        g.endScissor();
        if (bannerAction != null) {
            int ax = cx + cw - actionW - 2;
            boolean hover = mouseX >= ax && mouseX < ax + actionW && mouseY >= y + 2 && mouseY < y + 16;
            g.fill(ax, y + 2, ax + actionW, y + 16, hover ? accent().bright() : accent().accent());
            g.text(bannerAction, ax + 6, y + 5, accent().onAccent(), false);
        }
    }

    private boolean clickBanner(double mx, double my) {
        if (banner == null || bannerAction == null) {
            return false;
        }
        int cx = winX + sidebar + 6;
        int cw = winW - sidebar - 12;
        int y = winY + winH - PLAYER - 22;
        Minecraft mc = Minecraft.getInstance();
        int actionW = mc.font.width(bannerAction) + 12;
        int ax = cx + cw - actionW - 2;
        if (mx >= ax && mx < ax + actionW && my >= y + 2 && my < y + 16) {
            Runnable run = bannerRun;
            banner = null;
            run.run();
            return true;
        }
        return false;
    }

    private void drawOverlays(Gfx g, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (menu != null) {
            if (menu.width == 0) {
                menu.open(g, menuX, menuY, width, height);
            }
            menu.render(g, mouseX, mouseY, now);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (menu != null) {
            return menu.mouseClicked(mx, my, button, doubleClick);
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (dialog != null) {
            return dialog.mouseClicked(mx, my, button);
        }
        if (clickBanner(mx, my)) {
            return true;
        }
        for (Widget w : playerWidgets) {
            if (w.mouseClicked(mx, my, button, doubleClick)) {
                return true;
            }
        }
        if (SonocraftCore.get().session.status().state() != SessionState.CONNECTED) {
            return connection.mouseClicked(mx, my, button, doubleClick) || clickSidebar(mx, my, button);
        }
        if (back.mouseClicked(mx, my, button, doubleClick) || clickSidebar(mx, my, button)) {
            return true;
        }
        Page page = stack.peek();
        return page != null && page.mouseClicked(mx, my, button, doubleClick);
    }

    private boolean clickSidebar(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || mx < winX || mx >= winX + sidebar || my < winY
                || my >= winY + winH - PLAYER) {
            return false;
        }
        if (my >= sideTop() && my < sideBottom()) {
            int y = sideTop() - (int) sidebarScroll;
            for (SideRow r : sideRows()) {
                int h = rowHeight(r);
                if (my >= y && my < y + h) {
                    if (r.playlist() != null) {
                        select(Tab.PLAYLISTS);
                        open(new dev.opecko.sonocraft.ui.page.PlaylistPage(this, r.playlist()));
                    } else if (r.tab() == Tab.PLAYLISTS && mx >= winX + sidebar - 16) {
                        togglePlaylists();
                    } else if (r.tab() != null) {
                        select(r.tab());
                    }
                    return true;
                }
                y += h;
            }
            return true;
        }
        int by = sideBottom() + 4;
        if (my >= by && my < by + TAB_ROW) {
            Mc.setScreen(ConfigScreen.create(this));
            return true;
        }
        if (my >= by + TAB_ROW && my < by + 2 * TAB_ROW) {
            Mc.setScreen(new ConnectionScreen(this));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (Widget w : playerWidgets) {
            if (w.mouseReleased(event.x(), event.y(), event.button())) {
                return true;
            }
        }
        Page page = stack.peek();
        if (page != null && page.mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        for (Widget w : playerWidgets) {
            if (w.mouseDragged(event.x(), event.y(), event.button())) {
                return true;
            }
        }
        Page page = stack.peek();
        if (page != null && page.mouseDragged(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (overlayOpen()) {
            return dialog != null && dialog.mouseScrolled(mx, my, scrollY);
        }
        if (mx >= winX && mx < winX + sidebar && my >= sideTop() && my < sideBottom()) {
            sidebarScroll -= scrollY * TAB_ROW * 2;
            return true;
        }
        Page page = stack.peek();
        return page != null && page.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE && menu != null) {
            menu = null;
            return true;
        }
        if (dialog != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                closeDialog();
                return true;
            }
            if (dialog.keyPressed(key)) {
                return true;
            }
            return super.keyPressed(event);
        }
        boolean typing = getFocused() instanceof EditBox box && box.isFocused();
        if (event.hasControlDown() && key == InputConstants.KEY_F) {
            select(Tab.SEARCH);
            if (stack.peek() instanceof SearchPage search) {
                search.focusField();
            }
            return true;
        }
        Page page = stack.peek();
        // Enter in the search field is the page's, before the field swallows it
        if (page != null && typing && page.keyPressed(key, event.hasControlDown())) {
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        if (!typing && dev.opecko.sonocraft.input.Keybinds.isLibraryKey(event)) {
            onClose(); // the key that opened the library closes it
            return true;
        }
        if (!typing && key == InputConstants.KEY_BACKSPACE && stack.size() > 1) {
            back();
            return true;
        }
        return page != null && !typing && page.keyPressed(key, event.hasControlDown());
    }

    // ------------------------------------------------------------------ dialogs

    private void openDialog(Dialog d) {
        menu = null;
        dialog = d;
        for (EditBox f : fields) {
            f.visible = false;
        }
        d.opened();
    }

    private void closeDialog() {
        if (dialog != null) {
            dialog.closed();
        }
        dialog = null;
        for (EditBox f : fields) {
            f.visible = true;
        }
    }

    /** A modal box in the middle of the window. */
    private abstract class Dialog {
        int dx;
        int dy;
        int dw = 240;
        int dh = 90;

        void opened() {
        }

        void closed() {
        }

        void place() {
            dx = winX + (winW - dw) / 2;
            dy = winY + (winH - dh) / 2;
        }

        void renderOver(Gfx g, int mouseX, int mouseY, long now) {
            place();
            g.fill(winX, winY, winX + winW, winY + winH, UiColors.SCRIM);
            g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, UiColors.DIVIDER);
            g.fill(dx, dy, dx + dw, dy + dh, UiColors.MENU);
            render(g, mouseX, mouseY, now);
        }

        abstract void render(Gfx g, int mouseX, int mouseY, long now);

        abstract boolean mouseClicked(double mx, double my, int button);

        boolean mouseScrolled(double mx, double my, double amount) {
            return false;
        }

        boolean keyPressed(int key) {
            return false;
        }
    }

    private final class ConfirmDialog extends Dialog {
        private final Component title;
        private final Component message;
        private final TextButton yes;
        private final TextButton no;

        ConfirmDialog(Component title, Component message, Runnable onYes) {
            this.title = title;
            this.message = message;
            yes = new TextButton(CommonComponents.GUI_YES, () -> accent().accent(), true, () -> {
                closeDialog();
                onYes.run();
            });
            no = new TextButton(CommonComponents.GUI_NO, () -> 0, false, LibraryScreen.this::closeDialog);
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now) {
            g.text(title, dx + 8, dy + 8, UiColors.TEXT, false);
            g.wrappedText(message, dx + 8, dy + 22, dw - 16, UiColors.TEXT_DIM);
            yes.setBounds(dx + dw - 128, dy + dh - 24, 58, 18);
            no.setBounds(dx + dw - 66, dy + dh - 24, 58, 18);
            yes.render(g, mouseX, mouseY, now);
            no.render(g, mouseX, mouseY, now);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            return yes.mouseClicked(mx, my, button, false) || no.mouseClicked(mx, my, button, false) || true;
        }

        @Override
        boolean keyPressed(int key) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                yes.mouseClicked(yes.x + 1, yes.y + 1, InputConstants.MOUSE_BUTTON_LEFT, false);
                return true;
            }
            return false;
        }
    }

    private final class TextDialog extends Dialog {
        private final Component title;
        private final Consumer<String> onDone;
        private final EditBox field;
        private final TextButton ok;
        private final TextButton cancel;

        TextDialog(Component title, String initial, Consumer<String> onDone) {
            this.title = title;
            this.onDone = onDone;
            dh = 70;
            place();
            field = new EditBox(Minecraft.getInstance().font, dx + 8, dy + 22, dw - 16, 16, title);
            field.setMaxLength(150);
            field.setValue(initial);
            ok = new TextButton(CommonComponents.GUI_DONE, () -> accent().accent(), true, this::done);
            cancel = new TextButton(CommonComponents.GUI_CANCEL, () -> 0, false, LibraryScreen.this::closeDialog);
        }

        private void done() {
            String text = field.getValue().trim();
            if (!text.isEmpty()) {
                closeDialog();
                onDone.accept(text);
            }
        }

        @Override
        void opened() {
            addRenderableWidget(field);
            setFocused(field);
        }

        @Override
        void closed() {
            removeWidget(field);
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now) {
            g.text(title, dx + 8, dy + 8, UiColors.TEXT, false);
            field.setX(dx + 8);
            field.setY(dy + 22);
            ok.setBounds(dx + dw - 128, dy + dh - 24, 58, 18);
            cancel.setBounds(dx + dw - 66, dy + dh - 24, 58, 18);
            ok.active = !field.getValue().isBlank();
            ok.render(g, mouseX, mouseY, now);
            cancel.render(g, mouseX, mouseY, now);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            return ok.mouseClicked(mx, my, button, false) || cancel.mouseClicked(mx, my, button, false) || true;
        }

        @Override
        boolean keyPressed(int key) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                done();
                return true;
            }
            return false;
        }
    }

    /** Pick one of the user's own playlists (or make a new one) to add items to. */
    private final class PlaylistDialog extends Dialog {
        private final List<Ref> items;
        private final PagedLoader<Playlist> loader;
        private final ScrollList<Playlist> list;
        private final TextButton create;
        private final TextButton cancel;

        PlaylistDialog(List<Ref> items) {
            this.items = items;
            dw = 260;
            dh = Math.min(220, winH - 20);
            loader = new PagedLoader<>(c -> api().libraryPlaylists(c, null).thenApply(p ->
                    new dev.opecko.sonocraft.api.model.Page<>(p.items().stream()
                            .filter(pl -> pl != null && Boolean.TRUE.equals(pl.editable())).toList(), p.nextCursor())),
                    Component.translatable("sonocraft.ui.playlist.none"));
            list = new ScrollList<>(loader::items, ItemRow.HEIGHT, (g, p, i, x, y, w, h, hover, now) ->
                    ItemRow.render(g, LibraryScreen.this, p, x, y, w, h, hover, false, 0), (p, i, b, mx, my, dbl) -> {
                        closeDialog();
                        ItemActions.call(LibraryScreen.this, () -> api().playlistAddTracks(p.id(), items, null), r ->
                                LibraryScreen.this.notify(r.skipped() > 0
                                        ? Component.translatable("sonocraft.ui.done.added_skipped", p.title(), r.skipped())
                                        : Component.translatable("sonocraft.ui.done.added", p.title())));
                    }).onNearEnd(loader::loadMore);
            create = new TextButton(Component.translatable("sonocraft.ui.playlist.new"), () -> accent().accent(), true,
                    () -> askText(Component.translatable("sonocraft.ui.playlist.new"), "", title ->
                            ItemActions.call(LibraryScreen.this, () -> api().playlistCreate(title, null, null, items), r -> {
                                LibraryScreen.this.notify(Component.translatable("sonocraft.ui.done.added", title));
                                libraryChanged();
                            })));
            cancel = new TextButton(CommonComponents.GUI_CANCEL, () -> 0, false, LibraryScreen.this::closeDialog);
        }

        @Override
        void opened() {
            loader.ensure();
        }

        @Override
        void render(Gfx g, int mouseX, int mouseY, long now) {
            g.text(Component.translatable("sonocraft.ui.dialog.add_to_playlist"), dx + 8, dy + 8, UiColors.TEXT, false);
            list.setBounds(dx + 4, dy + 22, dw - 8, dh - 52);
            if (!loader.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
                list.render(g, mouseX, mouseY, now);
            }
            int cw = create.preferredWidth(g);
            create.setBounds(dx + 8, dy + dh - 24, cw, 18);
            cancel.setBounds(dx + dw - 66, dy + dh - 24, 58, 18);
            create.render(g, mouseX, mouseY, now);
            cancel.render(g, mouseX, mouseY, now);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            return create.mouseClicked(mx, my, button, false) || cancel.mouseClicked(mx, my, button, false)
                    || loader.mouseClicked(mx, my, button) || list.mouseClicked(mx, my, button, false) || true;
        }

        @Override
        boolean mouseScrolled(double mx, double my, double amount) {
            return list.mouseScrolled(mx, my, amount);
        }
    }
}
