package dev.opecko.sonocraft.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.api.ErrorCode;
import dev.opecko.sonocraft.api.NotConnectedException;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.compat.Gfx;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Loads one value for a page and draws the waiting, error, missing-permission and signed-out states (PLAN §6.2)
 * with a "Try again" or "Allow" button. Results arrive on the render thread.
 */
public final class Loader<T> {
    public enum State { IDLE, LOADING, READY, ERROR }

    private final Supplier<CompletableFuture<T>> source;
    private State state = State.IDLE;
    private T value;
    private Throwable error;
    private int generation;
    // button of the error state, in screen coordinates
    private int buttonX;
    private int buttonY;
    private int buttonW;

    public Loader(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    public State state() {
        return state;
    }

    public T value() {
        return value;
    }

    /** Starts loading unless it already has (or is loading). */
    public void ensure() {
        if (state == State.IDLE) {
            reload();
        }
    }

    public void reload() {
        int gen = ++generation;
        state = State.LOADING;
        CompletableFuture<T> f;
        try {
            f = source.get();
        } catch (RuntimeException e) {
            f = CompletableFuture.failedFuture(e);
        }
        f.whenCompleteAsync((v, e) -> {
            if (gen != generation) {
                return; // a newer load replaced this one
            }
            if (e != null) {
                error = unwrap(e);
                state = State.ERROR;
            } else {
                value = v;
                state = State.READY;
            }
        }, Minecraft.getInstance());
    }

    public static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * Draws the state when the value isn't ready. Returns true when it drew something (the page shows nothing
     * else then).
     */
    public boolean renderStatus(Gfx g, int x, int y, int width, int height, int mouseX, int mouseY, long now) {
        if (state == State.READY) {
            return false;
        }
        int cy = y + height / 2 - 10;
        if (state != State.ERROR) {
            String dots = ".".repeat((int) (now / 300 % 4));
            Component text = Component.translatable("sonocraft.ui.loading").append(dots);
            g.text(text, x + (width - g.textWidth(Component.translatable("sonocraft.ui.loading"))) / 2, cy,
                    UiColors.TEXT_DIM, false);
            buttonW = 0;
            return true;
        }
        Component message = describe(error);
        int used = g.wrappedText(message, x + 20, cy - 10, width - 40, UiColors.TEXT_DIM);
        Component button = buttonLabel(error);
        buttonW = g.textWidth(button) + 16;
        buttonX = x + (width - buttonW) / 2;
        buttonY = cy - 10 + used + 6;
        boolean hover = mouseX >= buttonX && mouseX < buttonX + buttonW && mouseY >= buttonY && mouseY < buttonY + 16;
        g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 16, hover ? 0x40FFFFFF : 0x20FFFFFF);
        g.text(button, buttonX + 8, buttonY + 4, UiColors.TEXT, false);
        return true;
    }

    /** Handles a click on the error state's button. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (state != State.ERROR || buttonW == 0 || button != InputConstants.MOUSE_BUTTON_LEFT
                || mx < buttonX || mx >= buttonX + buttonW || my < buttonY || my >= buttonY + 16) {
            return false;
        }
        Scope missing = missingScope(error);
        if (missing != null) {
            SonocraftCore.get().session.requestScopes(List.of(missing));
        } else {
            reload();
        }
        return true;
    }

    static Scope missingScope(Throwable e) {
        return e instanceof SonolithException se && se.is(ErrorCode.PERMISSION_DENIED) ? se.scope() : null;
    }

    private static Component buttonLabel(Throwable e) {
        return Component.translatable(missingScope(e) != null ? "sonocraft.ui.allow" : "sonocraft.ui.retry");
    }

    /** A sentence for the user about what went wrong. */
    public static Component describe(Throwable e) {
        Scope missing = missingScope(e);
        if (missing != null) {
            return Component.translatable("sonocraft.ui.error.permission",
                    Component.translatable("sonocraft.scope." + missing.name().toLowerCase(Locale.ROOT)));
        }
        if (e instanceof NotConnectedException) {
            return Component.translatable("sonocraft.ui.error.not_connected");
        }
        if (e instanceof SonolithException se) {
            return switch (se.error()) {
                case NOT_SIGNED_IN -> Component.translatable("sonocraft.ui.error.not_signed_in");
                case NOT_FOUND -> Component.translatable("sonocraft.ui.error.not_found");
                case TIMEOUT -> Component.translatable("sonocraft.ui.error.timeout");
                case RATE_LIMITED -> Component.translatable("sonocraft.ui.error.rate_limited");
                case UNAVAILABLE -> Component.translatable("sonocraft.ui.error.unavailable",
                        se.reason() != null ? se.reason() : se.error().wireName());
                default -> Component.translatable("sonocraft.ui.error.other", se.error().wireName());
            };
        }
        if (e instanceof dev.opecko.sonocraft.api.LocalRateLimitException) {
            return Component.translatable("sonocraft.ui.error.rate_limited");
        }
        return Component.translatable("sonocraft.ui.error.other", e.getClass().getSimpleName());
    }
}
