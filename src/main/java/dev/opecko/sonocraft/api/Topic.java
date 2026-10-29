package dev.opecko.sonocraft.api;

/** Event topics for events.subscribe (SPEC §10.1). */
public enum Topic {
    PLAYER_STATE("player.state", Scope.PLAYER_READ),
    PLAYER_TRACK("player.track", Scope.PLAYER_READ),
    PLAYER_POSITION("player.position", Scope.PLAYER_READ),
    PLAYER_VOLUME("player.volume", Scope.PLAYER_READ),
    QUEUE("queue", Scope.QUEUE_READ),
    LYRICS("lyrics", Scope.LYRICS_READ),
    LYRICS_LINE("lyrics.line", Scope.LYRICS_READ),
    LYRICS_WORD("lyrics.word", Scope.LYRICS_READ),
    ACCOUNT("account", null);

    private final String wire;
    private final Scope scope;

    Topic(String wire, Scope scope) {
        this.wire = wire;
        this.scope = scope;
    }

    public String wire() {
        return wire;
    }

    /** Required scope, or null if none is needed. */
    public Scope scope() {
        return scope;
    }
}
