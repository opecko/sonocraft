package dev.opecko.sonocraft.state;

import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.Json;
import dev.opecko.sonocraft.api.model.LyricLine;
import dev.opecko.sonocraft.api.model.LyricWord;
import dev.opecko.sonocraft.api.model.Lyrics;
import dev.opecko.sonocraft.api.model.LyricsLineEvent;
import dev.opecko.sonocraft.api.model.LyricsResult;
import dev.opecko.sonocraft.api.model.LyricsTier;
import dev.opecko.sonocraft.api.session.SessionListener;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lyrics of the current item from {@code lyrics.changed} (SPEC §16). The current line and word are worked out
 * locally from the interpolated playback position, so they move smoothly; {@code lyrics.line} only corrects the
 * line for a moment when Sonolith disagrees. Only synced lyrics (word or line tier) are kept. The lookups run on
 * the render thread and allocate nothing.
 */
public final class LyricsStore implements SessionListener {
    /** How long a {@code lyrics.line} event overrides the local line. */
    static final long CORRECTION_MS = 750;

    /** Synced lyrics for one track, or {@link #NONE}. */
    public record Synced(String trackId, LyricsTier tier, List<LyricLine> lines) {
        public boolean isEmpty() {
            return lines.isEmpty();
        }

        public boolean wordTimed() {
            return tier == LyricsTier.WORD;
        }
    }

    public static final Synced NONE = new Synced(null, null, List.of());

    private record LineCorrection(String trackId, long startMs, long receivedAtMs) {
    }

    private final AtomicReference<Synced> lyrics = new AtomicReference<>(NONE);
    private final AtomicReference<LineCorrection> correction = new AtomicReference<>();
    private final LongSupplier clock;

    public LyricsStore() {
        this(System::currentTimeMillis);
    }

    LyricsStore(LongSupplier clock) {
        this.clock = clock;
    }

    /** Synced lyrics, possibly of another track: check {@link Synced#trackId()} against the current item. */
    public Synced get() {
        return lyrics.get();
    }

    @Override
    public void onStatus(SessionStatus status) {
        if (status.state() != SessionState.CONNECTED) {
            lyrics.set(NONE);
            correction.set(null);
        }
    }

    @Override
    public void onEvent(String method, JsonObject params) {
        switch (method) {
            case "lyrics.changed" -> {
                LyricsResult r = Json.parse(params, LyricsResult.class);
                lyrics.set(r == null ? NONE : synced(r));
                correction.set(null);
            }
            case "lyrics.line" -> {
                LyricsLineEvent e = Json.parse(params, LyricsLineEvent.class);
                // a null line means "between lines": the last line stays current, so there is nothing to correct;
                // Sonolith's lineIndex also counts lines we dropped, so the line is matched by its start time
                if (e != null && e.trackId() != null && e.lineIndex() != null && e.startMs() != null) {
                    correction.set(new LineCorrection(e.trackId(), e.startMs(), clock.getAsLong()));
                }
            }
            default -> {
            }
        }
    }

    static Synced synced(LyricsResult r) {
        Lyrics l = r.lyrics();
        if (r.status() != LyricsResult.Status.FOUND || l == null || l.lines() == null
                || (l.tier() != LyricsTier.WORD && l.tier() != LyricsTier.LINE)) {
            return NONE;
        }
        // keep only lines with a start time, in order; the index into this list is the line number we use
        List<LyricLine> lines = l.lines().stream().filter(line -> line != null && line.startMs() != null).toList();
        return lines.isEmpty() ? NONE : new Synced(r.trackId(), l.tier(), lines);
    }

    /**
     * The line being sung at {@code positionMs}: the last line that has started, or -1 before the first one. A
     * recent {@code lyrics.line} for the same track wins over the local guess.
     */
    public int currentLine(Synced s, long positionMs) {
        LineCorrection c = correction.get();
        if (c != null && c.trackId().equals(s.trackId()) && clock.getAsLong() - c.receivedAtMs() < CORRECTION_MS) {
            int i = lineAt(s.lines(), c.startMs());
            if (i >= 0 && s.lines().get(i).startMs() == c.startMs()) {
                return i;
            }
        }
        return lineAt(s.lines(), positionMs);
    }

    /** The last line that has started at {@code positionMs}, or -1. */
    public static int lineAt(List<LyricLine> lines, long positionMs) {
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /**
     * How far through its time the line is, 0–1: from its start to its end, or to the next line's start when it
     * has no end ({@code nextStartMs} may be null for the last line).
     */
    public static double lineProgress(LyricLine line, Long nextStartMs, long positionMs) {
        long start = line.startMs();
        long end = line.endMs() != null && line.endMs() > start ? line.endMs()
                : nextStartMs != null && nextStartMs > start ? nextStartMs : start + 4000;
        return Math.max(0, Math.min(1, (double) (positionMs - start) / (end - start)));
    }

    /**
     * How much of the line has been sung, in words: the integer part is the number of finished words, the fraction
     * is the progress of the word being sung. Lines without word timings count as fully sung once they start.
     */
    public static double sungWords(LyricLine line, long positionMs) {
        List<LyricWord> words = line.words();
        if (words == null || words.isEmpty()) {
            return line.startMs() != null && positionMs >= line.startMs() ? Double.MAX_VALUE : 0;
        }
        for (int i = 0; i < words.size(); i++) {
            LyricWord w = words.get(i);
            if (positionMs < w.startMs()) {
                return i;
            }
            if (positionMs < w.endMs()) {
                long length = Math.max(1, w.endMs() - w.startMs());
                return i + (double) (positionMs - w.startMs()) / length;
            }
        }
        return words.size();
    }
}
