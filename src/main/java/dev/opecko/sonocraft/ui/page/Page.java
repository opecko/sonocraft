package dev.opecko.sonocraft.ui.page;

import com.google.gson.JsonObject;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.UiContext;
import net.minecraft.network.chat.Component;

/** One view in the content area of the Library screen (a list, a detail page…). */
public abstract class Page {
    protected final UiContext ui;
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    protected Page(UiContext ui) {
        this.ui = ui;
    }

    public abstract Component title();

    /** Called when the content area is (re)sized; lay out widgets here. */
    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** The page became visible (opened, or came back to after Back). */
    public void onShow() {
    }

    /** The page stopped being visible: remove text fields, stop timers. */
    public void onHide() {
    }

    public abstract void render(Gfx g, int mouseX, int mouseY, long now);

    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int key, boolean control) {
        return false;
    }

    /** Every Sonolith event while the page is visible (queue.changed, player.trackChanged…). */
    public void onEvent(String method, JsonObject params) {
    }
}
