package dev.opecko.sonocraft.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.Json;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonModelTest {
    private static final String TRACK = """
            {"type":"track","id":"t1","title":"Song","artists":[{"id":"a1","name":"A"},{"id":null,"name":"B"}],
             "album":{"id":null,"title":"Al"},"durationMs":200000,"thumbnailUrl":null,"explicit":true,"video":false,
             "rating":"like","someFutureField":{"x":1}}""";
    private static final String EPISODE = """
            {"type":"episode","id":"e1","title":"Ep","podcast":null,"author":"Host","durationMs":null,
             "thumbnailUrl":null,"publishedText":"Today","played":null,"resumePositionMs":null}""";

    @Test
    void parsesPlayerStateWithMediaItem() {
        String json = """
                {"status":"playing","item":%s,"positionMs":1500,"positionAt":1793021000000,"durationMs":200000,
                 "volume":70,"muted":false,"shuffle":true,"repeat":"all","rate":1,"episodeRate":1.5,
                 "canGoNext":true,"canGoPrevious":false,"queueIndex":3,"queueLength":10,"autoplay":true}"""
                .formatted(TRACK);
        PlayerState s = Json.parse(JsonParser.parseString(json), PlayerState.class);
        assertEquals(PlaybackStatus.PLAYING, s.status());
        Track t = assertInstanceOf(Track.class, s.item());
        assertEquals("A, B", t.artistNames(", "));
        assertEquals(Rating.LIKE, t.rating());
        assertEquals(new Ref(ItemType.TRACK, "t1"), t.ref());
        assertEquals(RepeatMode.ALL, s.repeat());
        assertEquals(1.5, s.episodeRate());
        assertEquals(3, s.queueIndex());
    }

    @Test
    void parsesMixedItemListsAndSkipsUnknownTypes() {
        String json = """
                {"topResult":{"type":"artist","id":"c1","name":"A","thumbnailUrl":null,"subscribersText":"1M"},
                 "items":[%s,%s,{"type":"hologram","id":"h1"},
                  {"type":"album","id":"b1","title":"X","artists":[],"albumType":"mixtape","year":2020,
                   "thumbnailUrl":null,"explicit":false}],
                 "nextCursor":null}""".formatted(TRACK, EPISODE);
        SearchResult r = Json.parse(JsonParser.parseString(json), SearchResult.class);
        assertInstanceOf(Artist.class, r.topResult());
        List<Item> items = r.items();
        assertInstanceOf(Track.class, items.get(0));
        assertInstanceOf(Episode.class, items.get(1));
        assertNull(items.get(2), "unknown item type");
        Album album = assertInstanceOf(Album.class, items.get(3));
        assertNull(album.albumType(), "unknown enum value");
        assertNull(r.nextCursor());
    }

    @Test
    void mediaItemRejectsNonMediaTypes() {
        String json = """
                {"entryId":"p0","item":{"type":"album","id":"b1","title":"X","artists":[],"albumType":null,
                 "year":null,"thumbnailUrl":null,"explicit":false}}""";
        assertNull(Json.parse(JsonParser.parseString(json), PlaylistEntry.class).item());
    }

    @Test
    void outcomeToStringHidesToken() {
        RegistrationOutcome o = new RegistrationOutcome("r", "register", "approved", "snl_secret", List.of(), null);
        assertFalse(o.toString().contains("snl_secret"));
    }
}
