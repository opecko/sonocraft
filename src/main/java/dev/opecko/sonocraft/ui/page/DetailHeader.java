package dev.opecko.sonocraft.ui.page;

import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** The top of a detail page: artwork, title, a line about it, and action buttons below. */
final class DetailHeader {
    static final int HEIGHT = 64;
    private static final int ART = 56;

    final List<Widget> buttons = new ArrayList<>();

    DetailHeader add(Widget button) {
        buttons.add(button);
        return this;
    }

    void render(Gfx g, UiContext ui, int x, int y, int width, String artUrl, boolean round, Component title,
                Component subtitle, int mouseX, int mouseY, long now) {
        ArtTextures.Art art = ui.thumbnail(artUrl, ART);
        if (art != null) {
            int radius = round ? art.size() / 2 : art.size() / 12;
            g.blit(ui.core().art.rounded(art, radius), x + 4, y + 4, ART, ART, 0, 0, art.size(), art.size(),
                    art.size(), art.size(), 0xFFFFFFFF);
        } else {
            g.fill(x + 4, y + 4, x + 4 + ART, y + 4 + ART, ui.accent().dim());
        }
        int tx = x + ART + 12;
        int tw = width - ART - 16;
        g.scissor(tx, y, tx + tw, y + HEIGHT);
        g.push();
        g.translate(tx, y + 6);
        g.scale(1.5f);
        g.text(title, 0, 0, UiColors.TEXT, false);
        g.pop();
        g.text(subtitle, tx, y + 22, UiColors.TEXT_DIM, false);
        g.endScissor();
        int bx = tx;
        for (Widget b : buttons) {
            int w = b instanceof dev.opecko.sonocraft.ui.widget.TextButton t ? t.preferredWidth(g) : 18;
            b.setBounds(bx, y + 40, w, 18);
            b.render(g, mouseX, mouseY, now);
            bx += w + 4;
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        for (Widget b : buttons) {
            if (b.mouseClicked(mx, my, button, false)) {
                return true;
            }
        }
        return false;
    }
}
