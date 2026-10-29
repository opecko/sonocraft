package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.api.SonolithApi;
import dev.opecko.sonocraft.api.model.Ref;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.theme.AccentPalette;
import dev.opecko.sonocraft.ui.page.Page;
import dev.opecko.sonocraft.ui.widget.ContextMenu;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** What pages may ask of the Library screen. All calls happen on the render thread. */
public interface UiContext {
    default SonocraftCore core() {
        return SonocraftCore.get();
    }

    default SonolithApi api() {
        return SonocraftCore.get().api;
    }

    AccentPalette accent();

    /** Opens a page on top of the current one (Back returns). */
    void open(Page page);

    void back();

    /** A short message at the bottom of the window. */
    void notify(Component message);

    /** Shows what went wrong with a call in words the user understands. */
    void error(Throwable error);

    void openMenu(ContextMenu menu, double mouseX, double mouseY);

    void confirm(Component title, Component message, Runnable onYes);

    /** Asks for a line of text (playlist name); {@code onDone} gets the trimmed, non-empty text. */
    void askText(Component title, String initial, Consumer<String> onDone);

    /** Lets the user pick one of their playlists (or create one) and adds the items to it. */
    void addToPlaylist(List<Ref> items);

    /** Playlists were created, renamed or deleted: lists that show them reload. */
    void libraryChanged();

    void addField(EditBox field);

    void removeField(EditBox field);

    void focus(EditBox field);

    /** A thumbnail texture if it is ready (loading starts otherwise). */
    ArtTextures.Art thumbnail(String url, int guiSize);
}
