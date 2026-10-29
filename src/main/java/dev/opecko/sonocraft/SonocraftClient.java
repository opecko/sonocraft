package dev.opecko.sonocraft;

import dev.opecko.sonocraft.command.SonocraftCommand;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.StatusReason;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.compat.Registrations;
import dev.opecko.sonocraft.compat.oneconfig.OneConfigIntegration;
import dev.opecko.sonocraft.input.Keybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SonocraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        Sonocraft.LOGGER.info("{} loaded on Minecraft {}", Sonocraft.NAME, mc);

        SonocraftCore core = SonocraftCore.init();
        core.session.addListener(new StatusNotifier());
        Keybinds.register();
        Registrations.hud(Identifier.fromNamespaceAndPath(Sonocraft.MOD_ID, "now_playing"), core.hud::renderHud);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> SonocraftCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SonocraftCommand.tick();
            core.artTracker.tick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(SonocraftClient::onboarding));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> OneConfigIntegration.registerHud());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            core.session.close();
            core.art.close();
        });
        // connects on its own with a stored token; without one it waits for the user to press Connect
        core.session.start();
    }

    /** Once per install: tell a user who never connected where to do it. */
    private static void onboarding() {
        SonocraftCore core = SonocraftCore.get();
        SessionStatus s = core.session.status();
        if (core.config.get().onboardingShown || s.state() != SessionState.NOT_CONNECTED
                || s.reason() != StatusReason.NONE) {
            return;
        }
        core.config.get().onboardingShown = true;
        core.config.save();
        Mc.toast(Component.literal(Sonocraft.NAME), Component.translatable("sonocraft.toast.onboarding"));
    }
}
