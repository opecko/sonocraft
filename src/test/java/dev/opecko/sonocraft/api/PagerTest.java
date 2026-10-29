package dev.opecko.sonocraft.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.model.Page;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class PagerTest {
    @Test
    void followsCursorsUntilNull() throws Exception {
        List<String> asked = new ArrayList<>();
        Pager<String> p = new Pager<>(cursor -> {
            asked.add(String.valueOf(cursor));
            return CompletableFuture.completedFuture(switch (String.valueOf(cursor)) {
                case "null" -> new Page<>(List.of("a", "b"), "c1");
                case "c1" -> new Page<>(Arrays.asList("c", null), "c2"); // short page, null = unknown type
                default -> new Page<>(List.of("d"), null);
            });
        });
        p.loadMore().get();
        assertTrue(p.hasMore());
        p.loadMore().get();
        assertEquals(List.of("d", "a", "b", "c").size(), p.items().size() + 1);
        p.loadMore().get();
        assertEquals(List.of("a", "b", "c", "d"), p.items());
        assertFalse(p.hasMore());
        assertEquals(List.of("null", "c1", "c2"), asked);
    }

    @Test
    void cursorExpiredRestartsFromTheFirstPage() throws Exception {
        List<String> asked = new ArrayList<>();
        Pager<String> p = new Pager<>(cursor -> {
            asked.add(String.valueOf(cursor));
            if ("c1".equals(cursor)) {
                JsonObject data = new JsonObject();
                data.addProperty("name", "InvalidParams");
                data.addProperty("reason", "cursorExpired");
                return CompletableFuture.failedFuture(new SonolithException(-32602, "InvalidParams", data, "x"));
            }
            return CompletableFuture.completedFuture(new Page<>(List.of("a"), "c1"));
        });
        p.loadMore().get();
        assertThrows(ExecutionException.class, () -> p.loadMore().get());
        assertTrue(p.items().isEmpty());
        p.loadMore().get();
        assertEquals(List.of("a"), p.items());
        assertEquals(List.of("null", "c1", "null"), asked);
    }
}
