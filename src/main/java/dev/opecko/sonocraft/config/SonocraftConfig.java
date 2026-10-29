package dev.opecko.sonocraft.config;

import dev.opecko.sonocraft.theme.ThemeManager;

/**
 * User settings, stored as JSON in {@code config/sonocraft.json}. No secrets here: the Sonolith token lives in the
 * platform secret store (PLAN §3.5). Fields are read on the render thread and changed on it by the config screen.
 * Only settings that are wired to something exist; later phases add their own groups.
 */
public final class SonocraftConfig {
    /** Bumped when a field changes meaning, so old files can be migrated. */
    public int schema = 1;
    public Connection connection = new Connection();
    public HudConfig hud = new HudConfig();
    public Controls controls = new Controls();
    public Theme theme = new Theme();
    public Library library = new Library();
    /** The one-time "connect to Sonolith" hint has been shown. */
    public boolean onboardingShown;

    public static final class Connection {
        /** Toasts for connection changes the user has to act on (approval, revoked, …). */
        public boolean toasts = true;
    }

    public static final class Controls {
        /** Action bar message after a playback key ("Paused", "Next"). */
        public boolean feedback = true;
    }

    public static final class Library {
        /** Pause a singleplayer game while the Library screen is open. */
        public boolean pauseGame = false;
    }

    public static final class Theme {
        public ThemeManager.Mode mode = ThemeManager.Mode.ART_OR_MANUAL;
        /** 0xRRGGBB. */
        public int manualColor = 0xE94560;
        public int transitionMs = 400;
    }

    /** When the HUD shows the connection state instead of the current track. */
    public enum StatusVisibility {
        NEVER,
        /** Only when something needs attention (not for a fresh install that was never connected). */
        ON_ERROR,
        ALWAYS
    }

    /** Fills sections missing from an old or hand-edited file with defaults. */
    SonocraftConfig normalize() {
        if (connection == null) {
            connection = new Connection();
        }
        if (hud == null) {
            hud = new HudConfig();
        }
        hud.normalize();
        if (controls == null) {
            controls = new Controls();
        }
        if (library == null) {
            library = new Library();
        }
        if (theme == null) {
            theme = new Theme();
        }
        if (theme.mode == null) {
            theme.mode = ThemeManager.Mode.ART_OR_MANUAL;
        }
        return this;
    }
}
