package dev.opecko.sonocraft.api;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Sonolith API permissions (SPEC §8). */
public enum Scope {
    PLAYER_READ("player:read"),
    PLAYER_CONTROL("player:control"),
    QUEUE_READ("queue:read"),
    QUEUE_WRITE("queue:write"),
    LIBRARY_READ("library:read"),
    LIBRARY_WRITE("library:write"),
    SEARCH("search"),
    LYRICS_READ("lyrics:read");

    private final String wire;

    Scope(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Scope by wire name, or null if unknown (newer Sonolith). */
    public static Scope fromWire(String wire) {
        for (Scope s : values()) {
            if (s.wire.equals(wire)) {
                return s;
            }
        }
        return null;
    }

    /** Known scopes from the given list; unknown ones are ignored. */
    public static Set<Scope> parseAll(Collection<String> wires) {
        EnumSet<Scope> out = EnumSet.noneOf(Scope.class);
        if (wires != null) {
            for (String w : wires) {
                Scope s = fromWire(w);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
