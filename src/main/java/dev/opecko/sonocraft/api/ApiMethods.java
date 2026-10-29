package dev.opecko.sonocraft.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rate class and required scopes of each method (SPEC §8, §12.1). */
public final class ApiMethods {
    /** API version this client is written against (MAJOR.MINOR). */
    public static final String API_VERSION = "1.0";

    private record Info(RateClass rate, List<Scope> scopes) {
    }

    private static final Map<String, Info> METHODS = new HashMap<>();

    static {
        auth("app.register", "app.requestScopes", "app.unregister", "session.hello");
        def(RateClass.LOCAL, List.of(), "app.info", "events.subscribe", "events.unsubscribe");
        def(RateClass.LOCAL, List.of(Scope.PLAYER_READ), "player.getState");
        def(RateClass.LOCAL, List.of(Scope.QUEUE_READ), "queue.get");
        def(RateClass.CONTROL, List.of(Scope.PLAYER_CONTROL), "player.play", "player.pause", "player.toggle",
                "player.next", "player.previous", "player.seek", "player.setVolume", "player.setShuffle",
                "player.setRepeat", "player.setRate", "player.playItem");
        def(RateClass.CONTROL, List.of(Scope.QUEUE_WRITE), "queue.add", "queue.playNext", "queue.move",
                "queue.remove", "queue.clear", "queue.setAutoplay");
        def(RateClass.NETWORK, List.of(Scope.LIBRARY_READ), "library.playlists", "library.likedSongs",
                "library.albums", "library.artists", "library.podcasts", "library.episodesForLater",
                "library.history");
        def(RateClass.NETWORK, List.of(Scope.LIBRARY_READ, Scope.SEARCH), "playlist.get");
        def(RateClass.NETWORK, List.of(Scope.SEARCH), "search.query", "search.suggestions", "browse.album",
                "browse.artist", "browse.podcast", "browse.episode");
        def(RateClass.NETWORK, List.of(Scope.LYRICS_READ), "lyrics.get");
        def(RateClass.ACCOUNT, List.of(Scope.LIBRARY_WRITE), "playlist.create", "playlist.rename",
                "playlist.delete", "playlist.addTracks", "playlist.removeTracks", "rating.set");
    }

    private static void auth(String... names) {
        def(RateClass.AUTH, List.of(), names);
    }

    private static void def(RateClass rate, List<Scope> scopes, String... names) {
        for (String n : names) {
            METHODS.put(n, new Info(rate, scopes));
        }
    }

    private ApiMethods() {
    }

    /** Rate class of a method; unknown methods fall into LOCAL. */
    public static RateClass rateClass(String method) {
        Info i = METHODS.get(method);
        return i == null ? RateClass.LOCAL : i.rate();
    }

    /** Scopes of which any one suffices (empty = no scope needed). */
    public static List<Scope> scopes(String method) {
        Info i = METHODS.get(method);
        return i == null ? List.of() : i.scopes();
    }

    public static boolean isKnown(String method) {
        return METHODS.containsKey(method);
    }
}
