package dev.opecko.sonocraft;

import dev.opecko.sonocraft.api.session.Notice;
import dev.opecko.sonocraft.api.session.SessionListener;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.StatusReason;
import dev.opecko.sonocraft.compat.Mc;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Toasts for status changes the user should act on (approval in Sonolith, access ended…). Routine outages
 * (Sonolith not running) get no toast; the HUD shows those.
 */
final class StatusNotifier implements SessionListener {
    private static final Set<StatusReason> TOASTED = EnumSet.of(
            StatusReason.WAITING_FOR_APPROVAL, StatusReason.IDENTITY_CHANGED, StatusReason.DENIED,
            StatusReason.EXPIRED, StatusReason.BLOCKED, StatusReason.ALREADY_PENDING, StatusReason.REQUEST_CLOSED,
            StatusReason.ACCESS_ENDED, StatusReason.REVOKED_REMOVED, StatusReason.REVOKED_BLOCKED,
            StatusReason.REVOKED_UNREGISTERED, StatusReason.REVOKED_REPLACED, StatusReason.IDENTITY_NOT_APPROVED,
            StatusReason.UNSUPPORTED_VERSION, StatusReason.DISCONNECTED, StatusReason.DISCONNECTED_UNREACHABLE);

    private SessionStatus last = SessionStatus.INITIAL;

    @Override
    public void onStatus(SessionStatus status) {
        SessionStatus prev = last;
        last = status;
        if (!SonocraftCore.get().config.get().connection.toasts) {
            return;
        }
        Component title = Component.literal(Sonocraft.NAME);
        if (status.state() == SessionState.CONNECTED && prev.state() != SessionState.CONNECTED
                && (prev.state() == SessionState.REGISTERING || prev.state() == SessionState.IDENTITY_PENDING)) {
            show(title, Component.translatable("sonocraft.toast.connected"));
        } else if (status.reason() != prev.reason() && TOASTED.contains(status.reason())) {
            show(title, Texts.status(status));
        } else if (status.reason() == StatusReason.NOT_AVAILABLE && prev.state() == SessionState.CONNECTING
                && prev.reason() == StatusReason.NONE && status.state() == SessionState.NOT_CONNECTED) {
            // registration after a click, but Sonolith isn't running
            show(title, Texts.status(status));
        }
    }

    @Override
    public void onNotice(Notice notice) {
        String key = "sonocraft.notice." + notice.kind().name().toLowerCase(Locale.ROOT);
        Component body = notice.retryMs() > 0
                ? Component.translatable(key + ".wait", Texts.duration(notice.retryMs()))
                : Component.translatable(key);
        show(Component.literal(Sonocraft.NAME), body);
    }

    private static void show(Component title, Component body) {
        Minecraft.getInstance().execute(() -> Mc.toast(title, body));
    }
}
