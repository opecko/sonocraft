package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.api.Pager;
import dev.opecko.sonocraft.api.model.Page;
import dev.opecko.sonocraft.compat.Gfx;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A {@link Pager} with the states of {@link Loader}: shows loading / error / empty while there is nothing to list,
 * and loads further pages when the list scrolls near its end.
 */
public final class PagedLoader<T> {
    private final Pager<T> pager;
    private final Loader<List<T>> firstPage;
    private final Component emptyText;
    private boolean started;
    private boolean failedMore;

    public PagedLoader(Function<String, CompletableFuture<Page<T>>> fetch, Component emptyText) {
        this.pager = new Pager<>(fetch);
        this.firstPage = new Loader<>(pager::loadMore);
        this.emptyText = emptyText;
    }

    public List<T> items() {
        return pager.items();
    }

    public void ensure() {
        if (!started) {
            started = true;
            firstPage.reload();
        }
    }

    /** Starts over from the first page (after the list changed). */
    public void reload() {
        pager.reset();
        failedMore = false;
        started = true;
        firstPage.reload();
    }

    /** Called when the list nears its end. */
    public void loadMore() {
        if (firstPage.state() != Loader.State.READY || failedMore || !pager.hasMore() || pager.isLoading()) {
            return;
        }
        pager.loadMore().whenCompleteAsync((r, e) -> failedMore = e != null, Minecraft.getInstance());
    }

    /** Draws loading / error / empty when there are no items. Returns true when it drew something. */
    public boolean renderStatus(Gfx g, int x, int y, int width, int height, int mouseX, int mouseY, long now) {
        if (firstPage.renderStatus(g, x, y, width, height, mouseX, mouseY, now)) {
            return true;
        }
        if (pager.items().isEmpty() && !pager.hasMore()) {
            g.text(emptyText, x + (width - g.textWidth(emptyText)) / 2, y + height / 2 - 4, UiColors.TEXT_DIM, false);
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return firstPage.mouseClicked(mx, my, button);
    }
}
