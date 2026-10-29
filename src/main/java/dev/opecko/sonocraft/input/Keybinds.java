package dev.opecko.sonocraft.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.api.LocalRateLimitException;
import dev.opecko.sonocraft.api.NotConnectedException;
import dev.opecko.sonocraft.api.SonolithException;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.compat.Registrations;
import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.hud.HudEditorScreen;
import dev.opecko.sonocraft.ui.LibraryScreen;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Sonocraft keys (PLAN §7). Default codes are positional: on Czech QWERTZ {@code =} sits where US {@code -} is,
 * {@code )} where {@code ]} is and {@code ú} where {@code [} is. One call per press (consumeClick), not while held.
 */
public final class Keybinds {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Sonocraft.MOD_ID, "main"));

    private static KeyMapping playPause;
    private static KeyMapping next;
    private static KeyMapping previous;
    private static KeyMapping library;
    private static KeyMapping toggleHud;
    private static KeyMapping hudEditor;

    private Keybinds() {
    }

    public static void register() {
        playPause = key("play_pause", InputConstants.KEY_MINUS);
        next = key("next", InputConstants.KEY_RBRACKET);
        previous = key("previous", InputConstants.KEY_LBRACKET);
        library = key("library", InputConstants.KEY_J);
        // optional, unbound by default
        toggleHud = key("toggle_hud", InputConstants.UNKNOWN.getValue());
        hudEditor = key("hud_editor", InputConstants.UNKNOWN.getValue());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    /** The library key, as the player bound it; the open library closes on it too. */
    public static boolean isLibraryKey(KeyEvent event) {
        return library != null && library.matches(event);
    }

    private static KeyMapping key(String name, int code) {
        return Registrations.keyMapping(new KeyMapping("key.sonocraft." + name, code, CATEGORY));
    }

    private static void tick() {
        SonocraftCore core = SonocraftCore.get();
        while (playPause.consumeClick()) {
            boolean playing = core.player.get().isPlaying();
            run(core.api::toggle, playing ? "sonocraft.feedback.paused" : "sonocraft.feedback.playing");
        }
        while (next.consumeClick()) {
            run(core.api::next, "sonocraft.feedback.next");
        }
        while (previous.consumeClick()) {
            run(core.api::previous, "sonocraft.feedback.previous");
        }
        while (library.consumeClick()) {
            Mc.setScreen(new LibraryScreen(null));
        }
        while (toggleHud.consumeClick()) {
            HudConfig hud = core.config.get().hud;
            hud.enabled = !hud.enabled;
            core.config.save();
            Mc.actionBar(Component.translatable(hud.enabled ? "sonocraft.feedback.hud_on" : "sonocraft.feedback.hud_off"));
        }
        while (hudEditor.consumeClick()) {
            Mc.setScreen(new HudEditorScreen(() -> null));
        }
    }

    private static void run(Supplier<CompletableFuture<Void>> action, String feedbackKey) {
        action.get().whenComplete((r, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null) {
                if (SonocraftCore.get().config.get().controls.feedback) {
                    Mc.actionBar(Component.translatable(feedbackKey));
                }
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof LocalRateLimitException) {
                return; // pressing too fast: drop silently
            }
            if (cause instanceof NotConnectedException) {
                Mc.actionBar(Component.translatable("sonocraft.feedback.not_connected"));
            } else if (cause instanceof SonolithException e) {
                Mc.actionBar(Component.translatable("sonocraft.feedback.error", e.error().wireName()));
            } else {
                Mc.actionBar(Component.translatable("sonocraft.feedback.error", cause.getClass().getSimpleName()));
            }
        }));
    }
}
