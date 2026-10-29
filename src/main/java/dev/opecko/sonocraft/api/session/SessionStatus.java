package dev.opecko.sonocraft.api.session;

import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.model.AppIdentity;
import java.util.Set;

/**
 * Immutable snapshot of the session state.
 *
 * @param retryAtMs epoch ms for the countdown (cooldown, next attempt), or 0
 * @param signedIn  YouTube sign-in in Sonolith, null if unknown
 * @param detail    technical detail for {@link StatusReason#ERROR} (error name), otherwise null
 */
public record SessionStatus(SessionState state, StatusReason reason, long retryAtMs, Set<Scope> granted,
                            Boolean signedIn, AppIdentity identity, String apiVersion, String appVersion,
                            String detail) {
    public static final SessionStatus INITIAL = new SessionStatus(SessionState.NOT_CONNECTED, StatusReason.NONE, 0,
            Set.of(), null, null, null, null, null);

    public SessionStatus {
        granted = Set.copyOf(granted);
    }

    public boolean has(Scope scope) {
        return granted.contains(scope);
    }

    /** Remaining ms until {@code retryAtMs}, or 0. */
    public long remainingMs(long nowMs) {
        return retryAtMs == 0 ? 0 : Math.max(0, retryAtMs - nowMs);
    }

    SessionStatus with(SessionState state, StatusReason reason) {
        return new SessionStatus(state, reason, 0, granted, signedIn, identity, apiVersion, appVersion, null);
    }

    SessionStatus withRetryAt(long retryAtMs) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }

    SessionStatus withGranted(Set<Scope> granted) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }

    SessionStatus withSignedIn(Boolean signedIn) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }

    SessionStatus withIdentity(AppIdentity identity) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }

    SessionStatus withVersions(String apiVersion, String appVersion) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }

    SessionStatus withDetail(String detail) {
        return new SessionStatus(state, reason, retryAtMs, granted, signedIn, identity, apiVersion, appVersion,
                detail);
    }
}
