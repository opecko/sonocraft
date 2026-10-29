package dev.opecko.sonocraft.api.model;

import java.util.List;

/** One page of a list; `nextCursor == null` means the end (SPEC §13.6). */
public record Page<T>(List<T> items, String nextCursor) {
    public boolean hasMore() {
        return nextCursor != null;
    }
}
