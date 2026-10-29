package dev.opecko.sonocraft.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.opecko.sonocraft.api.model.Album;
import dev.opecko.sonocraft.api.model.AlbumDetail;
import dev.opecko.sonocraft.api.model.Artist;
import dev.opecko.sonocraft.api.model.ArtistDetail;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.EpisodeDetail;
import dev.opecko.sonocraft.api.model.HistoryEntry;
import dev.opecko.sonocraft.api.model.LyricsResult;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.Page;
import dev.opecko.sonocraft.api.model.PlayItemResult;
import dev.opecko.sonocraft.api.model.PlayerState;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.PlaylistAddResult;
import dev.opecko.sonocraft.api.model.PlaylistPage;
import dev.opecko.sonocraft.api.model.PlaylistRemoveResult;
import dev.opecko.sonocraft.api.model.Podcast;
import dev.opecko.sonocraft.api.model.PodcastPage;
import dev.opecko.sonocraft.api.model.Privacy;
import dev.opecko.sonocraft.api.model.QueueAddResult;
import dev.opecko.sonocraft.api.model.QueuePage;
import dev.opecko.sonocraft.api.model.QueueRemoveResult;
import dev.opecko.sonocraft.api.model.QueueResult;
import dev.opecko.sonocraft.api.model.Rating;
import dev.opecko.sonocraft.api.model.Ref;
import dev.opecko.sonocraft.api.model.RepeatMode;
import dev.opecko.sonocraft.api.model.SearchResult;
import dev.opecko.sonocraft.api.model.SearchSuggestions;
import dev.opecko.sonocraft.api.model.ServerInfo;
import dev.opecko.sonocraft.api.session.SonolithSession;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Typed calls for all Sonolith API v1.0 methods (SPEC §15) on top of {@link SonolithSession}. Paged methods take
 * {@code cursor} (null = first page) and {@code limit} (null = default 50). Calls without a result return
 * {@code CompletableFuture<Void>}.
 */
public final class SonolithApi {
    /** Values for {@link #searchQuery}. */
    public enum SearchType { ALL, SONGS, VIDEOS, ALBUMS, ARTISTS, PLAYLISTS, PODCASTS, EPISODES }

    private static final Type PLAYLISTS = new TypeToken<Page<Playlist>>() { }.getType();
    private static final Type MEDIA_ITEMS = new TypeToken<Page<MediaItem>>() { }.getType();
    private static final Type ALBUMS = new TypeToken<Page<Album>>() { }.getType();
    private static final Type ARTISTS = new TypeToken<Page<Artist>>() { }.getType();
    private static final Type PODCASTS = new TypeToken<Page<Podcast>>() { }.getType();
    private static final Type EPISODES = new TypeToken<Page<Episode>>() { }.getType();
    private static final Type HISTORY = new TypeToken<Page<HistoryEntry>>() { }.getType();

    private final SonolithSession session;

    public SonolithApi(SonolithSession session) {
        this.session = session;
    }

    private <T> CompletableFuture<T> call(String method, Json.Params params, Type type) {
        return session.call(method, params.build()).thenApply(r -> Json.<T>parse(r, type));
    }

    private CompletableFuture<Void> run(String method, Json.Params params) {
        return session.call(method, params.build()).thenApply(r -> null);
    }

    private static Json.Params p() {
        return Json.params();
    }

    private static Map<String, String> ref(Ref r) {
        return Map.of("type", r.type().wire(), "id", r.id());
    }

    private static List<Map<String, String>> refs(List<Ref> refs) {
        return refs.stream().map(SonolithApi::ref).toList();
    }

    // ---------------------------------------------------------------- app
    public CompletableFuture<ServerInfo> appInfo() {
        return call("app.info", p(), ServerInfo.class);
    }

    // ---------------------------------------------------------------- player
    public CompletableFuture<PlayerState> getState() {
        return call("player.getState", p(), PlayerState.class);
    }

    public CompletableFuture<Void> play() {
        return run("player.play", p());
    }

    public CompletableFuture<Void> pause() {
        return run("player.pause", p());
    }

    public CompletableFuture<Void> toggle() {
        return run("player.toggle", p());
    }

    public CompletableFuture<Void> next() {
        return run("player.next", p());
    }

    public CompletableFuture<Void> previous() {
        return run("player.previous", p());
    }

    public CompletableFuture<Void> seekTo(long positionMs) {
        return run("player.seek", p().put("positionMs", positionMs));
    }

    public CompletableFuture<Void> seekBy(long offsetMs) {
        return run("player.seek", p().put("offsetMs", offsetMs));
    }

    /** Volume 0–100 and/or mute; null = unchanged (at least one must be given). */
    public CompletableFuture<Void> setVolume(Integer volume, Boolean muted) {
        return run("player.setVolume", p().put("volume", volume).put("muted", muted));
    }

    public CompletableFuture<Void> setShuffle(boolean enabled) {
        return run("player.setShuffle", p().put("enabled", enabled));
    }

    public CompletableFuture<Void> setRepeat(RepeatMode mode) {
        return run("player.setRepeat", p().put("mode", mode.wire()));
    }

    /** Episode speed: 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3. */
    public CompletableFuture<Void> setRate(double rate) {
        return run("player.setRate", p().put("rate", rate));
    }

    /** Plays an item, replacing the queue. {@code startId} = id of the track to start from (album/playlist). */
    public CompletableFuture<PlayItemResult> playItem(Ref item, String startId, Boolean shuffle, Long positionMs) {
        return call("player.playItem", p().put("item", ref(item)).put("startId", startId).put("shuffle", shuffle)
                .put("positionMs", positionMs), PlayItemResult.class);
    }

    // ---------------------------------------------------------------- queue
    public CompletableFuture<QueuePage> queueGet(Integer fromIndex, String cursor, Integer limit) {
        return call("queue.get", p().put("fromIndex", fromIndex).put("cursor", cursor).put("limit", limit),
                QueuePage.class);
    }

    public CompletableFuture<QueueAddResult> queueAdd(List<Ref> items) {
        return call("queue.add", p().put("items", refs(items)), QueueAddResult.class);
    }

    public CompletableFuture<QueueAddResult> queuePlayNext(List<Ref> items) {
        return call("queue.playNext", p().put("items", refs(items)), QueueAddResult.class);
    }

    public CompletableFuture<QueueResult> queueMove(String entryId, int toIndex) {
        return call("queue.move", p().put("entryId", entryId).put("toIndex", toIndex), QueueResult.class);
    }

    public CompletableFuture<QueueRemoveResult> queueRemove(List<String> entryIds) {
        return call("queue.remove", p().put("entryIds", entryIds), QueueRemoveResult.class);
    }

    public CompletableFuture<QueueResult> queueClear() {
        return call("queue.clear", p(), QueueResult.class);
    }

    public CompletableFuture<QueueResult> queueSetAutoplay(boolean enabled) {
        return call("queue.setAutoplay", p().put("enabled", enabled), QueueResult.class);
    }

    // ---------------------------------------------------------------- library
    private Json.Params page(String cursor, Integer limit) {
        return p().put("cursor", cursor).put("limit", limit);
    }

    public CompletableFuture<Page<Playlist>> libraryPlaylists(String cursor, Integer limit) {
        return call("library.playlists", page(cursor, limit), PLAYLISTS);
    }

    public CompletableFuture<Page<MediaItem>> libraryLikedSongs(String cursor, Integer limit) {
        return call("library.likedSongs", page(cursor, limit), MEDIA_ITEMS);
    }

    public CompletableFuture<Page<Album>> libraryAlbums(String cursor, Integer limit) {
        return call("library.albums", page(cursor, limit), ALBUMS);
    }

    public CompletableFuture<Page<Artist>> libraryArtists(String cursor, Integer limit) {
        return call("library.artists", page(cursor, limit), ARTISTS);
    }

    public CompletableFuture<Page<Podcast>> libraryPodcasts(String cursor, Integer limit) {
        return call("library.podcasts", page(cursor, limit), PODCASTS);
    }

    public CompletableFuture<Page<Episode>> libraryEpisodesForLater(String cursor, Integer limit) {
        return call("library.episodesForLater", page(cursor, limit), EPISODES);
    }

    public CompletableFuture<Page<HistoryEntry>> libraryHistory(String cursor, Integer limit) {
        return call("library.history", page(cursor, limit), HISTORY);
    }

    // ---------------------------------------------------------------- playlists
    public CompletableFuture<PlaylistPage> playlistGet(String id, String cursor, Integer limit) {
        return call("playlist.get", page(cursor, limit).put("id", id), PlaylistPage.class);
    }

    public CompletableFuture<Playlist> playlistCreate(String title, String description, Privacy privacy,
                                                      List<Ref> items) {
        return call("playlist.create", p().put("title", title).put("description", description)
                .put("privacy", privacy != null ? privacy.wire() : null)
                .put("items", items != null ? refs(items) : null), Playlist.class);
    }

    public CompletableFuture<Void> playlistRename(String id, String title) {
        return run("playlist.rename", p().put("id", id).put("title", title));
    }

    public CompletableFuture<Void> playlistDelete(String id) {
        return run("playlist.delete", p().put("id", id));
    }

    public CompletableFuture<PlaylistAddResult> playlistAddTracks(String id, List<Ref> items,
                                                                  Boolean allowDuplicates) {
        return call("playlist.addTracks", p().put("id", id).put("items", refs(items))
                .put("allowDuplicates", allowDuplicates), PlaylistAddResult.class);
    }

    public CompletableFuture<PlaylistRemoveResult> playlistRemoveTracks(String id, List<String> entryIds) {
        return call("playlist.removeTracks", p().put("id", id).put("entryIds", entryIds), PlaylistRemoveResult.class);
    }

    // ---------------------------------------------------------------- search & browse
    public CompletableFuture<SearchResult> searchQuery(String query, SearchType type, String cursor, Integer limit) {
        return call("search.query", page(cursor, limit).put("query", query)
                .put("type", type != null ? type.name().toLowerCase(java.util.Locale.ROOT) : null), SearchResult.class);
    }

    public CompletableFuture<SearchSuggestions> searchSuggestions(String query) {
        return call("search.suggestions", p().put("query", query), SearchSuggestions.class);
    }

    public CompletableFuture<AlbumDetail> browseAlbum(String id) {
        return call("browse.album", p().put("id", id), AlbumDetail.class);
    }

    public CompletableFuture<ArtistDetail> browseArtist(String id) {
        return call("browse.artist", p().put("id", id), ArtistDetail.class);
    }

    public CompletableFuture<PodcastPage> browsePodcast(String id, String cursor, Integer limit) {
        return call("browse.podcast", page(cursor, limit).put("id", id), PodcastPage.class);
    }

    public CompletableFuture<EpisodeDetail> browseEpisode(String id) {
        return call("browse.episode", p().put("id", id), EpisodeDetail.class);
    }

    // ---------------------------------------------------------------- rating & lyrics
    public CompletableFuture<Void> ratingSet(Ref item, Rating rating) {
        return run("rating.set", p().put("item", ref(item)).put("rating", rating.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** Lyrics of the current item ({@code trackId == null}) or of the given track. */
    public CompletableFuture<LyricsResult> lyricsGet(String trackId) {
        return call("lyrics.get", p().put("trackId", trackId), LyricsResult.class);
    }

    /** Raw call for methods this version doesn't know yet. */
    public CompletableFuture<JsonElement> raw(String method, JsonObject params) {
        return session.call(method, params);
    }
}
