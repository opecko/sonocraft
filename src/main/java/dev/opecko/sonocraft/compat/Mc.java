package dev.opecko.sonocraft.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client API differences between MC versions in one place. Call only from the game thread. 26.2 split {@code Gui}
 * into {@code Gui} (screens, toasts) and {@code Hud}.
 */
public final class Mc {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);

    private Mc() {
    }

    /** Sonocraft system toast; a new toast replaces the previous one. */
    public static void toast(Component title, Component body) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, title, body);
        //?} else {
        /*SystemToast.addOrUpdate(mc.getToastManager(), TOAST, title, body);
        *///?}
    }

    /** Short message above the hotbar (action bar). */
    public static void actionBar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        mc.gui.hud.setOverlayMessage(message, false);
        //?} else {
        /*mc.gui.setOverlayMessage(message, false);
        *///?}
    }

    /** Chat message (only in a world). */
    public static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        //? if >=26.1 {
        mc.player.sendSystemMessage(message);
        //?} else {
        /*mc.player.displayClientMessage(message, false);
        *///?}
    }

    /** HUD is hidden (F1) or the debug screen is open (F3). */
    public static boolean hudHidden() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        return mc.gui.hud.isHidden() || mc.gui.hud.getDebugOverlay().showDebugScreen();
        //?} else {
        /*return mc.options.hideGui || mc.getDebugOverlay().showDebugScreen();
        *///?}
    }

    public static void setScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    public static Screen screen() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }
}
