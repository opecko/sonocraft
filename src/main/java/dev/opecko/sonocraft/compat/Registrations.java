package dev.opecko.sonocraft.compat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Fabric API registrations whose names differ between versions. */
public final class Registrations {
    private Registrations() {
    }

    public static KeyMapping keyMapping(KeyMapping key) {
        //? if >=26.1 {
        return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(key);
        //?} else {
        /*return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(key);
        *///?}
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        //? if >=26.1 {
        return net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(name);
        //?} else {
        /*return net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(name);
        *///?}
    }

    /** HUD element rendered after the vanilla HUD. */
    public static void hud(Identifier id, Consumer<Gfx> painter) {
        HudElementRegistry.addLast(id, (graphics, delta) -> painter.accept(new Gfx(graphics)));
    }
}
