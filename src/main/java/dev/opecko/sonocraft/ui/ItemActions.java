package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.api.ApiMethods;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.model.Album;
import dev.opecko.sonocraft.api.model.ArtistCredit;
import dev.opecko.sonocraft.api.model.Artist;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.api.model.ItemType;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.Podcast;
import dev.opecko.sonocraft.api.model.QueueAddResult;
import dev.opecko.sonocraft.api.model.Rating;
import dev.opecko.sonocraft.api.model.Ref;
import dev.opecko.sonocraft.api.model.Track;
import dev.opecko.sonocraft.ui.page.AlbumPage;
import dev.opecko.sonocraft.ui.page.ArtistPage;
import dev.opecko.sonocraft.ui.page.PlaylistPage;
import dev.opecko.sonocraft.ui.page.PodcastPage;
import dev.opecko.sonocraft.ui.widget.ContextMenu;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * What clicking an item does, and the actions of its context menu (PLAN §6.2): play, play next, add to queue, add
 * to a playlist, like/dislike, go to album or artist, playlist editing. Actions whose scope isn't granted are shown
 * greyed with the reason.
 */
public final class ItemActions {
    /**
     * Where an item is listed. {@code container} makes a click play the album/playlist from that item;
     * {@code playlist} + {@code entryId} allow "Remove from playlist".
     */
    public record Origin(Ref container, String containerTitle, Playlist playlist, String entryId) {
        public static final Origin NONE = new Origin(null, null, null, null);

        public static Origin in(Ref container, String title) {
            return new Origin(container, title, null, null);
        }
    }

    /** Called after a change that a page shows (rename, delete, removed entry), so it can reload. */
    public interface Changed {
        void changed();
    }

    private ItemActions() {
    }

    /** Left click: play songs and episodes, open everything else. */
    public static void activate(UiContext ui, Item item, Origin origin) {
        switch (item) {
            case MediaItem m -> {
                if (origin.container() != null) {
                    play(ui, origin.container(), origin.containerTitle(), m.id(), false);
                } else {
                    play(ui, m.ref(), null, null, false);
                }
            }
            case Album a -> ui.open(new AlbumPage(ui, a.id()));
            case Playlist p -> ui.open(new PlaylistPage(ui, p));
            case Artist a -> ui.open(new ArtistPage(ui, a.id()));
            case Podcast p -> ui.open(new PodcastPage(ui, p.id()));
        }
    }

    /** Plays an item (replacing the queue); {@code fromTitle} is the collection shown as "Playing from". */
    public static void play(UiContext ui, Ref item, String fromTitle, String startId, boolean shuffle) {
        call(ui, () -> ui.api().playItem(item, startId, shuffle ? Boolean.TRUE : null, null), r -> {
            ui.core().playingFrom.set(fromTitle);
            if (r.truncated()) {
                ui.notify(Component.translatable("sonocraft.ui.done.truncated", r.limit()));
            }
        });
    }

    /** Runs a call and reports a failure; {@code then} gets the result on the render thread. */
    public static <T> void call(UiContext ui, Supplier<CompletableFuture<T>> call, Consumer<T> then) {
        CompletableFuture<T> f;
        try {
            f = call.get();
        } catch (RuntimeException e) {
            f = CompletableFuture.failedFuture(e);
        }
        f.whenCompleteAsync((r, e) -> {
            if (e != null) {
                ui.error(Loader.unwrap(e));
            } else {
                then.accept(r);
            }
        }, Minecraft.getInstance());
    }

    private static boolean allowed(UiContext ui, String method) {
        List<Scope> needed = ApiMethods.scopes(method);
        return needed.isEmpty() || needed.stream().anyMatch(ui.core().session.status()::has);
    }

    /** Adds the entry, or a greyed one saying which permission it needs. */
    private static void entry(UiContext ui, ContextMenu menu, String key, String method, Runnable action) {
        Component label = Component.translatable(key);
        if (allowed(ui, method)) {
            menu.add(label, action);
        } else {
            Scope scope = ApiMethods.scopes(method).get(0);
            menu.addDisabled(label, Component.translatable("sonocraft.ui.needs_permission",
                    Component.translatable("sonocraft.scope." + scope.name().toLowerCase(Locale.ROOT))));
        }
    }

    public static ContextMenu menu(UiContext ui, Item item, Origin origin, Changed changed) {
        return fill(new ContextMenu(), ui, item, origin, changed);
    }

    /** Adds the item's actions to a menu that may already hold others (the queue's own entries). */
    public static ContextMenu fill(ContextMenu m, UiContext ui, Item item, Origin origin, Changed changed) {
        Ref ref = item.ref();
        boolean playable = item.type() != ItemType.ARTIST;
        boolean collection = item.type() == ItemType.ALBUM || item.type() == ItemType.PLAYLIST
                || item.type() == ItemType.PODCAST;

        if (collection || item instanceof Artist) {
            m.add(Component.translatable("sonocraft.ui.action.open"), () -> activate(ui, item, Origin.NONE));
        }
        if (playable) {
            if (item instanceof MediaItem && origin.container() != null) {
                entry(ui, m, "sonocraft.ui.action.play_from_here", "player.playItem",
                        () -> play(ui, origin.container(), origin.containerTitle(), item.id(), false));
            }
            String from = collection ? item.displayTitle() : null;
            entry(ui, m, "sonocraft.ui.action.play", "player.playItem", () -> play(ui, ref, from, null, false));
            if (collection) {
                entry(ui, m, "sonocraft.ui.action.shuffle", "player.playItem", () -> play(ui, ref, from, null, true));
            }
            entry(ui, m, "sonocraft.ui.action.play_next", "queue.playNext", () -> call(ui,
                    () -> ui.api().queuePlayNext(List.of(ref)), r -> added(ui, r, "sonocraft.ui.done.play_next")));
            entry(ui, m, "sonocraft.ui.action.add_to_queue", "queue.add", () -> call(ui,
                    () -> ui.api().queueAdd(List.of(ref)), r -> added(ui, r, "sonocraft.ui.done.queued")));
        }
        if (item instanceof MediaItem) {
            entry(ui, m, "sonocraft.ui.action.add_to_playlist", "playlist.addTracks",
                    () -> ui.addToPlaylist(List.of(ref)));
        }
        if (origin.playlist() != null && origin.entryId() != null && Boolean.TRUE.equals(origin.playlist().editable())) {
            entry(ui, m, "sonocraft.ui.action.remove_from_playlist", "playlist.removeTracks", () -> call(ui,
                    () -> ui.api().playlistRemoveTracks(origin.playlist().id(), List.of(origin.entryId())), r -> {
                        ui.notify(Component.translatable("sonocraft.ui.done.removed"));
                        changed.changed();
                    }));
        }

        if (playable) {
            m.separator();
            Rating rating = item instanceof Track t ? t.rating() : null;
            if (rating == Rating.LIKE) {
                entry(ui, m, "sonocraft.ui.action.unlike", "rating.set", () -> rate(ui, ref, Rating.NONE));
            } else {
                entry(ui, m, collection ? "sonocraft.ui.action.save" : "sonocraft.ui.action.like", "rating.set",
                        () -> rate(ui, ref, Rating.LIKE));
            }
            if (item instanceof MediaItem && rating != Rating.DISLIKE) {
                entry(ui, m, "sonocraft.ui.action.dislike", "rating.set", () -> rate(ui, ref, Rating.DISLIKE));
            }
        }

        if (item instanceof Track t) {
            if (t.album() != null && t.album().id() != null) {
                m.add(Component.translatable("sonocraft.ui.action.go_album"),
                        () -> ui.open(new AlbumPage(ui, t.album().id())));
            }
            if (t.artists() != null) {
                for (ArtistCredit a : t.artists()) {
                    if (a.id() != null) {
                        m.add(Component.translatable("sonocraft.ui.action.go_artist", a.name()),
                                () -> ui.open(new ArtistPage(ui, a.id())));
                    }
                }
            }
        }
        if (item instanceof Episode e && e.podcast() != null && e.podcast().id() != null) {
            m.add(Component.translatable("sonocraft.ui.action.go_podcast"),
                    () -> ui.open(new PodcastPage(ui, e.podcast().id())));
        }
        if (item instanceof Playlist p && Boolean.TRUE.equals(p.editable())) {
            m.separator();
            playlistEditing(ui, m, p, changed);
        }
        return m;
    }

    /** Rename and delete for a playlist the user owns. */
    public static void playlistEditing(UiContext ui, ContextMenu m, Playlist p, Changed changed) {
        entry(ui, m, "sonocraft.ui.action.rename", "playlist.rename", () -> ui.askText(
                Component.translatable("sonocraft.ui.dialog.rename"), p.title(), title -> call(ui,
                        () -> ui.api().playlistRename(p.id(), title), r -> {
                            ui.notify(Component.translatable("sonocraft.ui.done.renamed"));
                            ui.libraryChanged();
                            changed.changed();
                        })));
        entry(ui, m, "sonocraft.ui.action.delete", "playlist.delete", () -> ui.confirm(
                Component.translatable("sonocraft.ui.dialog.delete"),
                Component.translatable("sonocraft.ui.dialog.delete.message", p.title()), () -> call(ui,
                        () -> ui.api().playlistDelete(p.id()), r -> {
                            ui.notify(Component.translatable("sonocraft.ui.done.deleted"));
                            ui.libraryChanged();
                            changed.changed();
                        })));
    }

    private static void rate(UiContext ui, Ref ref, Rating rating) {
        call(ui, () -> ui.api().ratingSet(ref, rating), r -> ui.notify(Component.translatable(
                "sonocraft.ui.done.rating." + rating.name().toLowerCase(Locale.ROOT))));
    }

    private static void added(UiContext ui, QueueAddResult r, String key) {
        ui.notify(r.truncated() ? Component.translatable("sonocraft.ui.done.truncated", r.limit())
                : Component.translatable(key));
    }
}
