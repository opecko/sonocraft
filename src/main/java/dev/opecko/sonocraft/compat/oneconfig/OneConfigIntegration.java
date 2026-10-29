package dev.opecko.sonocraft.compat.oneconfig;

import dev.opecko.sonocraft.Sonocraft;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional OneConfig integration. Nothing here touches OneConfig classes unless OneConfig is installed, so the mod
 * runs fine without it.
 */
public final class OneConfigIntegration {
    private static boolean registered;

    private OneConfigIntegration() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("oneconfig");
    }

    /** OneConfig's HUD editor is open (always false without OneConfig). */
    public static boolean isEditorOpen() {
        return registered && OneConfigHud.isEditorOpen();
    }

    /** Puts the Now Playing panel into OneConfig's HUD editor. Call once the client has started. */
    public static void registerHud() {
        if (registered || !isLoaded()) {
            return;
        }
        registered = true;
        try {
            new OneConfigHud().register();
        } catch (LinkageError | RuntimeException e) {
            // another OneConfig version without this API: our own HUD editor still works
            Sonocraft.LOGGER.warn("Cannot add the HUD to OneConfig's HUD editor: {}", e.toString());
        }
    }
}
