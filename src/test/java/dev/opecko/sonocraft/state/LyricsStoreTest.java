package dev.opecko.sonocraft.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.model.LyricLine;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LyricsStoreTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static final String WORD_LYRICS = """
            {"trackId":"t1","status":"found","lyrics":{"trackId":"t1","tier":"word","provider":"lrclib",
             "providerName":"LRCLIB","attribution":null,"lines":[
              {"startMs":null,"endMs":null,"text":"[intro]","words":[],"background":[]},
              {"startMs":12400,"endMs":15900,"text":"Lights on the water","words":[
                {"startMs":12400,"endMs":12900,"text":"Lights "},{"startMs":12900,"endMs":13300,"text":"on "},
                {"startMs":13300,"endMs":13600,"text":"the "},{"startMs":13600,"endMs":15900,"text":"water"}],
               "background":[]},
              {"startMs":16100,"endMs":19000,"text":"Second line","words":[],"background":[]},
              {"startMs":20000,"endMs":21000,"text":"Third","words":[],"background":[]}]}}""";

    @Test
    void keepsSyncedLinesAndFindsTheCurrentOne() {
        LyricsStore store = new LyricsStore();
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        LyricsStore.Synced s = store.get();
        assertEquals("t1", s.trackId());
        assertTrue(s.wordTimed());
        assertEquals(3, s.lines().size(), "the unsynced [intro] line is dropped");
        assertEquals(-1, store.currentLine(s, 1000));
        assertEquals(0, store.currentLine(s, 12400));
        assertEquals(0, store.currentLine(s, 16000), "between lines the last started line stays current");
        assertEquals(1, store.currentLine(s, 16100));
        assertEquals(2, store.currentLine(s, 999_999));
    }

    @Test
    void countsSungWordsWithProgress() {
        LyricsStore store = new LyricsStore();
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        LyricLine line = store.get().lines().get(0);
        assertEquals(0, LyricsStore.sungWords(line, 12000));
        assertEquals(0.5, LyricsStore.sungWords(line, 12650), 1e-9);
        assertEquals(2, LyricsStore.sungWords(line, 13300), 1e-9);
        assertEquals(4, LyricsStore.sungWords(line, 20000), 1e-9);
        LyricLine plain = store.get().lines().get(1);
        assertEquals(0, LyricsStore.sungWords(plain, 16000));
        assertEquals(Double.MAX_VALUE, LyricsStore.sungWords(plain, 16100));
    }

    @Test
    void lineEventCorrectsBrieflyForTheSameTrack() {
        AtomicLong now = new AtomicLong(1000);
        LyricsStore store = new LyricsStore(now::get);
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        LyricsStore.Synced s = store.get();
        store.onEvent("lyrics.line", json("""
                {"trackId":"t1","lineIndex":2,"text":"Third","startMs":20000,"endMs":21000,"positionMs":20000}"""));
        assertEquals(2, store.currentLine(s, 12500));
        now.addAndGet(LyricsStore.CORRECTION_MS);
        assertEquals(0, store.currentLine(s, 12500));
        store.onEvent("lyrics.line", json("""
                {"trackId":"other","lineIndex":1,"text":"x","startMs":1,"endMs":2,"positionMs":1}"""));
        assertEquals(0, store.currentLine(s, 12500));
    }

    @Test
    void endOfLineEventKeepsTheLastLineAndIndexesAreMatchedByTime() {
        AtomicLong now = new AtomicLong(1000);
        LyricsStore store = new LyricsStore(now::get);
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        LyricsStore.Synced s = store.get();
        // the line ended and the next hasn't started: Sonolith sends no line
        store.onEvent("lyrics.line", json("""
                {"trackId":"t1","lineIndex":null,"text":null,"startMs":null,"endMs":null,"positionMs":15950}"""));
        assertEquals(0, store.currentLine(s, 15950), "must not jump back to the start of the lyrics");
        // Sonolith's index 2 counts the dropped [intro] line; its start time is our line 1
        store.onEvent("lyrics.line", json("""
                {"trackId":"t1","lineIndex":2,"text":"Second line","startMs":16100,"endMs":19000,"positionMs":16000}"""));
        assertEquals(1, store.currentLine(s, 16000));
    }

    @Test
    void lineProgressRunsFromStartToEndOrNextLine() {
        LyricsStore store = new LyricsStore();
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        LyricLine second = store.get().lines().get(1);
        assertEquals(0, LyricsStore.lineProgress(second, 20000L, 16100), 1e-9);
        assertEquals(0.5, LyricsStore.lineProgress(second, 20000L, 17550), 1e-9);
        assertEquals(1, LyricsStore.lineProgress(second, 20000L, 30000), 1e-9);
        LyricLine noEnd = new LyricLine(1000L, null, "x", java.util.List.of(), java.util.List.of());
        assertEquals(0.5, LyricsStore.lineProgress(noEnd, 3000L, 2000), 1e-9);
    }

    @Test
    void ignoresUnsyncedInstrumentalAndNotFound() {
        LyricsStore store = new LyricsStore();
        store.onEvent("lyrics.changed", json(WORD_LYRICS));
        store.onEvent("lyrics.changed", json("""
                {"trackId":"t2","status":"found","lyrics":{"trackId":"t2","tier":"unsynced","provider":"x",
                 "providerName":"X","attribution":null,"lines":[{"startMs":null,"endMs":null,"text":"a",
                 "words":[],"background":[]}]}}"""));
        assertSame(LyricsStore.NONE, store.get());
        store.onEvent("lyrics.changed", json("{\"trackId\":\"t3\",\"status\":\"instrumental\",\"lyrics\":null}"));
        assertSame(LyricsStore.NONE, store.get());
    }
}
