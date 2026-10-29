package dev.opecko.sonocraft.art;

import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.state.PlayerStore;
import dev.opecko.sonocraft.theme.ThemeManager;
import java.util.Objects;

/**
 * Follows the current item's artwork: loads it when the track changes and feeds its colour to the theme. The old
 * accent stays until the new art is ready, so the colour doesn't flash to the fallback between tracks.
 * Runs on the render thread.
 */
public final class ArtTracker {
    private int size = 128;
    private boolean reload;

    private final PlayerStore player;
    private final ArtTextures textures;
    private final ThemeManager theme;
    private String url;
    private ArtTextures.Art art;

    public ArtTracker(PlayerStore player, ArtTextures textures, ThemeManager theme) {
        this.player = player;
        this.textures = textures;
        this.theme = theme;
        textures.onLoaded((key, art) -> {
            if (url != null && key.equals(ArtTextures.key(url, size))) {
                this.art = art;
                theme.setArtColor(art != null ? art.accent() : -1, System.currentTimeMillis());
            }
        });
    }

    /** Texture size in real pixels the HUD wants; a change loads the art again at that size. */
    public void setSize(int px) {
        int wanted = Math.max(32, Math.min(512, (px + 31) / 32 * 32));
        if (wanted != size) {
            size = wanted;
            reload = true;
        }
    }

    public void tick() {
        MediaItem item = player.get().item();
        String now = item != null ? item.thumbnailUrl() : null;
        boolean sameTrack = Objects.equals(now, url);
        if (sameTrack && !reload) {
            return;
        }
        reload = false;
        url = now;
        if (!sameTrack) {
            art = null;
        }
        if (now == null) {
            theme.setArtColor(-1, System.currentTimeMillis());
            return;
        }
        ArtTextures.Art loaded = textures.get(now, size);
        if (loaded != null) {
            art = loaded;
            theme.setArtColor(loaded.accent(), System.currentTimeMillis());
        }
    }

    /** The current artwork texture, or null while loading / without art. Cheap enough for every frame. */
    public ArtTextures.Art current() {
        return art;
    }
}
