package dev.opecko.sonocraft.platform.token;

import dev.opecko.sonocraft.platform.Os;
import java.nio.file.Path;

/**
 * Token store for the current platform. The location is per user, not per MC instance, so different instances
 * don't overwrite each other's registration (PLAN §3.4).
 */
public final class TokenStores {
    private TokenStores() {
    }

    public static TokenStore forPlatform() {
        if (Os.current() == Os.WINDOWS) {
            Path dir = Path.of(envOr("APPDATA", System.getProperty("user.home")), "Sonocraft");
            return new FallbackTokenStore(
                    new FileTokenStore(dir.resolve("token.dpapi"), new DpapiCodec(), "DPAPI file"),
                    new FileTokenStore(dir.resolve("token"), FileTokenStore.Codec.PLAIN, "file"));
        }
        String dataHome = System.getenv("XDG_DATA_HOME");
        Path dir = (dataHome != null && !dataHome.isEmpty()
                ? Path.of(dataHome) : Path.of(System.getProperty("user.home"), ".local", "share")).resolve("sonocraft");
        TokenStore file = new FileTokenStore(dir.resolve("token"), FileTokenStore.Codec.PLAIN, "file");
        SecretToolTokenStore secret = SecretToolTokenStore.findOnPath();
        return secret != null ? new FallbackTokenStore(secret, file) : file;
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? v : fallback;
    }
}
