package dev.opecko.sonocraft.api;

import dev.opecko.sonocraft.api.model.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Incremental loading of a paged list (SPEC §13.6). A page may be shorter than the limit even when more follow;
 * only {@code nextCursor == null} means the end. On {@code cursorExpired} it restarts from the first page.
 * Thread-safe; {@link #items()} returns an immutable snapshot for the render thread.
 */
public final class Pager<T> {
    private final Function<String, CompletableFuture<Page<T>>> fetch;
    private volatile List<T> items = List.of();
    private String cursor;
    private boolean started;
    private boolean done;
    private CompletableFuture<List<T>> loading;
    private int generation;

    public Pager(Function<String, CompletableFuture<Page<T>>> fetch) {
        this.fetch = fetch;
    }

    public List<T> items() {
        return items;
    }

    public synchronized boolean hasMore() {
        return !done;
    }

    public synchronized boolean isLoading() {
        return loading != null && !loading.isDone();
    }

    /** Loads the next page (or returns the load in progress). The result is the whole list so far. */
    public synchronized CompletableFuture<List<T>> loadMore() {
        if (loading != null && !loading.isDone()) {
            return loading;
        }
        if (done) {
            return CompletableFuture.completedFuture(items);
        }
        int gen = generation;
        String from = started ? cursor : null;
        started = true;
        loading = fetch.apply(from).handle((page, error) -> {
            synchronized (this) {
                if (gen != generation) {
                    return items;
                }
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    if (cause instanceof SonolithException e && e.is(ErrorCode.INVALID_PARAMS)
                            && "cursorExpired".equals(e.reason())) {
                        resetLocked();
                    }
                    throw new CompletionException(cause);
                }
                List<T> next = new ArrayList<>(items);
                if (page.items() != null) {
                    page.items().stream().filter(java.util.Objects::nonNull).forEach(next::add);
                }
                items = List.copyOf(next);
                cursor = page.nextCursor();
                done = cursor == null;
                return items;
            }
        });
        return loading;
    }

    /** Drops loaded items; the next {@link #loadMore()} starts from the beginning. */
    public synchronized void reset() {
        resetLocked();
    }

    private void resetLocked() {
        generation++;
        items = List.of();
        cursor = null;
        started = false;
        done = false;
        loading = null;
    }
}
