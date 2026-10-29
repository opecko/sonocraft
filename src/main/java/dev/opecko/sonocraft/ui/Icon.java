package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.compat.Gfx;
import net.minecraft.resources.Identifier;

/**
 * White 12×12 icons in {@code textures/gui/ui_icons.png}, tinted when drawn; the order matches the sheet. Based on
 * pixelarticons by Gerrit Halfmann (MIT, see {@code LICENSE-pixelarticons.txt}), halved from their 24 px grid;
 * PREVIOUS, NEXT and the filled ones are drawn from them. Draw at 12 (or 24) so the pixels stay square.
 */
public enum Icon {
    PLAY, PAUSE, PREVIOUS, NEXT, SHUFFLE, REPEAT, REPEAT_ONE, VOLUME, MUTE, HEART, HEART_FULL, BACK, SEARCH, MORE,
    PLUS, CLOSE, SETTINGS, DRAG, CHECK, QUEUE, CHEVRON_RIGHT, CHEVRON_DOWN, MUSIC, PLAY_FULL;

    public static final int SIZE = 12;
    private static final Identifier SHEET = Identifier.fromNamespaceAndPath(Sonocraft.MOD_ID, "textures/gui/ui_icons.png");

    public void draw(Gfx g, int x, int y, int size, int argb) {
        int count = values().length;
        g.blit(SHEET, x, y, size, size, ordinal() * SIZE, 0, SIZE, SIZE, count * SIZE, SIZE, argb);
    }

    public void draw(Gfx g, int x, int y, int argb) {
        draw(g, x, y, SIZE, argb);
    }
}
