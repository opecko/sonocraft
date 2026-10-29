package dev.opecko.sonocraft;

import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.session.AppRegistration;
import dev.opecko.sonocraft.api.session.AppRegistration.ScopeSpec;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** What the user sees on Sonolith's consent screen (SPEC §6.2). Texts in English, one sentence each. */
public final class SonocraftRegistration {
    public static final String HOMEPAGE = "https://github.com/opecko/sonocraft";

    private SonocraftRegistration() {
    }

    public static AppRegistration create() {
        Map<Scope, ScopeSpec> scopes = new LinkedHashMap<>();
        scopes.put(Scope.PLAYER_READ, new ScopeSpec(true, "To show the current song in Minecraft's HUD."));
        scopes.put(Scope.PLAYER_CONTROL, new ScopeSpec(true, "To play, pause and skip songs with in-game keys."));
        scopes.put(Scope.LYRICS_READ, new ScopeSpec(false, "To show synced lyrics in the HUD."));
        scopes.put(Scope.QUEUE_READ, new ScopeSpec(false, "To show your queue in the in-game library."));
        scopes.put(Scope.QUEUE_WRITE, new ScopeSpec(false, "To add, move and remove songs in your queue from the game."));
        scopes.put(Scope.LIBRARY_READ, new ScopeSpec(false, "To browse your playlists, liked songs and albums in the game."));
        scopes.put(Scope.SEARCH, new ScopeSpec(false, "To search music and open albums and artists in the game."));
        scopes.put(Scope.LIBRARY_WRITE, new ScopeSpec(false, "To like songs and edit your playlists from the game."));
        return new AppRegistration(Sonocraft.APP_ID, Sonocraft.NAME,
                "Shows what's playing in Minecraft and lets you control Sonolith from the game.",
                "opecko", HOMEPAGE, icon(), scopes);
    }

    /** Mod icon scaled to 64×64 as a PNG data URI, or null. */
    private static String icon() {
        try (InputStream in = SonocraftRegistration.class.getResourceAsStream("/assets/sonocraft/icon.png")) {
            if (in == null) {
                return null;
            }
            BufferedImage src = ImageIO.read(in);
            BufferedImage dst = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            var g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, 64, 64, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, "png", out);
            String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
            return uri.length() <= 65_536 ? uri : null;
        } catch (IOException | RuntimeException e) {
            Sonocraft.LOGGER.warn("Cannot encode the registration icon: {}", e.toString());
            return null;
        }
    }
}
