package dev.opecko.sonocraft;

import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.StatusReason;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

/** Localized connection status texts and small text helpers. */
public final class Texts {
    private Texts() {
    }

    public static Component status(SessionStatus s) {
        StatusReason reason = s.reason();
        if (reason == StatusReason.NONE) {
            return Component.translatable("sonocraft.state." + s.state().name().toLowerCase(Locale.ROOT));
        }
        String key = "sonocraft.status." + reason.name().toLowerCase(Locale.ROOT);
        long remaining = s.remainingMs(System.currentTimeMillis());
        if (reason == StatusReason.ERROR) {
            return Component.translatable(key, s.detail() != null ? s.detail() : "?");
        }
        if ((s.state() == SessionState.COOLDOWN || s.state() == SessionState.UNAVAILABLE) && remaining > 0) {
            return Component.translatable(key + ".wait", duration(remaining));
        }
        return Component.translatable(key);
    }

    /** "45 s", "9 min", "1 h". */
    public static String duration(long ms) {
        long s = (ms + 999) / 1000;
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return (s + 59) / 60 + " min";
        }
        return (s + 3599) / 3600 + " h";
    }

    /** "m:ss" or "h:mm:ss". */
    public static String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = total / 60 % 60;
        long sec = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Joins the parts that are not null or blank with " · ". */
    public static String dotted(String... parts) {
        return Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" · "));
    }
}
