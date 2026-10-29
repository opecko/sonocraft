package dev.opecko.sonocraft.api.model;

import java.util.List;

/** registration.resolved: `kind` register/scopes/identity, `status` approved/denied/blocked/expired. */
public record RegistrationOutcome(String requestId, String kind, String status, String token,
                                  List<String> grantedScopes, Long retryAfterMs) {
    @Override
    public String toString() {
        // the token must never end up in the log
        return "RegistrationOutcome[requestId=" + requestId + ", kind=" + kind + ", status=" + status
                + ", grantedScopes=" + grantedScopes + ", retryAfterMs=" + retryAfterMs + "]";
    }
}
