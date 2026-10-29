package dev.opecko.sonocraft.api.session;

/** Event subscription options: position rate 1–4 Hz and word-level lyrics. */
public record SubscriptionOptions(int positionHz, boolean lyricsWords) {
    public static final SubscriptionOptions DEFAULT = new SubscriptionOptions(1, false);

    public SubscriptionOptions {
        positionHz = Math.max(1, Math.min(4, positionHz));
    }
}
