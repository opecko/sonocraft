package dev.opecko.sonocraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.SonolithSession;
import dev.opecko.sonocraft.compat.Mc;
import dev.opecko.sonocraft.compat.Registrations;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import dev.opecko.sonocraft.config.ConfigScreen;
import dev.opecko.sonocraft.hud.HudEditorScreen;
import dev.opecko.sonocraft.ui.ConnectionScreen;
import dev.opecko.sonocraft.ui.LibraryScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * {@code /sonocraft connect|disconnect|cancel|status|config|connection}. Connect is a user action, so it may start
 * registration.
 */
public final class SonocraftCommand {
    /** A screen to open on the next tick: the chat screen closes right after a command and would replace it. */
    private static Supplier<Screen> pendingScreen;

    private SonocraftCommand() {
    }

    /** Called every client tick. */
    public static void tick() {
        Supplier<Screen> s = pendingScreen;
        if (s != null) {
            pendingScreen = null;
            Mc.setScreen(s.get());
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(Registrations.literal("sonocraft")
                .executes(SonocraftCommand::status)
                .then(Registrations.literal("status").executes(SonocraftCommand::status))
                .then(Registrations.literal("connect").executes(SonocraftCommand::connect))
                .then(Registrations.literal("cancel").executes(SonocraftCommand::cancel))
                .then(Registrations.literal("disconnect").executes(SonocraftCommand::disconnect))
                .then(Registrations.literal("config").executes(ctx -> open(() -> ConfigScreen.create(null))))
                .then(Registrations.literal("connection").executes(ctx -> open(() -> new ConnectionScreen(null))))
                .then(Registrations.literal("hud").executes(ctx -> open(() -> new HudEditorScreen(() -> null))))
                .then(Registrations.literal("library").executes(ctx -> open(() -> new LibraryScreen(null)))));
    }

    private static int open(Supplier<Screen> screen) {
        pendingScreen = screen;
        return 1;
    }

    private static SonolithSession session() {
        return SonocraftCore.get().session;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        SessionStatus s = session().status();
        ctx.getSource().sendFeedback(Component.literal(Sonocraft.NAME + ": ").append(Texts.status(s)));
        return 1;
    }

    private static int connect(CommandContext<FabricClientCommandSource> ctx) {
        switch (session().connect()) {
            case STARTED -> ctx.getSource().sendFeedback(Component.translatable("sonocraft.command.connecting"));
            case BUSY -> ctx.getSource().sendFeedback(Texts.status(session().status()));
            case COOLDOWN -> ctx.getSource().sendError(Texts.status(session().status()));
        }
        return 1;
    }

    private static int cancel(CommandContext<FabricClientCommandSource> ctx) {
        session().cancelRegistration();
        return 1;
    }

    private static int disconnect(CommandContext<FabricClientCommandSource> ctx) {
        session().disconnect().whenComplete((reached, error) ->
                Minecraft.getInstance().execute(() -> Mc.chat(Texts.status(session().status()))));
        return 1;
    }
}
