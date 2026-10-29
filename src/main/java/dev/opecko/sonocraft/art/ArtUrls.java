package dev.opecko.sonocraft.art;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Artwork URLs. YouTube Music art on googleusercontent/ggpht ends with size options such as {@code =w544-h544};
 * replacing them asks for exactly the size we draw, as JPEG ({@code -rj}), which ImageIO decodes without a WebP
 * plugin. Other URLs (i.ytimg.com video thumbnails) are used as they are. URLs are never used as ids (SPEC §13.7).
 */
public final class ArtUrls {
    private static final Pattern SIZE_OPTIONS = Pattern.compile("=[swh]\\d+[-\\w]*$");

    private ArtUrls() {
    }

    public static String sized(String url, int px) {
        if (url == null || !isResizable(url)) {
            return url;
        }
        String base = SIZE_OPTIONS.matcher(url).find() ? url.substring(0, url.lastIndexOf('=')) : url;
        return base + "=w" + px + "-h" + px + "-l90-rj";
    }

    static boolean isResizable(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.endsWith(".googleusercontent.com") || host.endsWith(".ggpht.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
