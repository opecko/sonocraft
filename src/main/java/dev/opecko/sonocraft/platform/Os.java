package dev.opecko.sonocraft.platform;

import java.util.Locale;

public enum Os {
    LINUX,
    WINDOWS,
    OTHER;

    public static Os current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        if (name.contains("linux")) {
            return LINUX;
        }
        return OTHER;
    }
}
