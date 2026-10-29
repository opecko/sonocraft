package dev.opecko.sonocraft;

import dev.opecko.sonocraft.api.SonolithApi;
import dev.opecko.sonocraft.api.session.SonolithSession;
import dev.opecko.sonocraft.art.ArtTextures;
import dev.opecko.sonocraft.art.ArtTracker;
import dev.opecko.sonocraft.config.ConfigStore;
import dev.opecko.sonocraft.config.SonocraftConfig;
import dev.opecko.sonocraft.hud.HudRenderer;
import dev.opecko.sonocraft.theme.ThemeManager;
import dev.opecko.sonocraft.platform.Connector;
import dev.opecko.sonocraft.platform.token.TokenStores;
import dev.opecko.sonocraft.api.session.SubscriptionOptions;
import dev.opecko.sonocraft.state.LyricsStore;
import dev.opecko.sonocraft.state.PlayerStore;
import net.fabricmc.loader.api.FabricLoader;

/** Shared mod services (config, session, API, stores). Created at client startup. */
public final class SonocraftCore {
    private static SonocraftCore instance;

    public final SonolithSession session;
    public final SonolithApi api;
    public final PlayerStore player = new PlayerStore();
    public final LyricsStore lyrics = new LyricsStore();
    public final ThemeManager theme = new ThemeManager();
    public final ArtTextures art = new ArtTextures(32);
    public final ArtTracker artTracker = new ArtTracker(player, art, theme);
    public final HudRenderer hud = new HudRenderer();
    private int subscribedHz;
    public final ConfigStore config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sonocraft.json"));

    private SonocraftCore() {
        config.load();
        session = new SonolithSession(SonocraftRegistration.create(), TokenStores.forPlatform(), Connector.system());
        api = new SonolithApi(session);
        session.addListener(player);
        session.addListener(lyrics);
        applyConfig();
    }

    /** Pushes config values into the services that cache them. Called after loading and after every save. */
    public void applyConfig() {
        SonocraftConfig.Theme t = config.get().theme;
        theme.configure(t.mode, t.manualColor, t.transitionMs, System.currentTimeMillis());
        // positions twice a second keep lyrics in step; word events are not needed, words are timed locally
        int positionHz = config.get().hud.lyrics.enabled ? 2 : 1;
        if (positionHz != subscribedHz) {
            subscribedHz = positionHz;
            session.setSubscriptionOptions(() -> new SubscriptionOptions(positionHz, false));
            session.resubscribe();
        }
    }

    static SonocraftCore init() {
        instance = new SonocraftCore();
        return instance;
    }

    public static SonocraftCore get() {
        return instance;
    }
}
