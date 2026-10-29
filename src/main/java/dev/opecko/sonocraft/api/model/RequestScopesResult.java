package dev.opecko.sonocraft.api.model;

import java.util.List;

/** `status` is "pending" or "granted". */
public record RequestScopesResult(String status, String requestId, Long expiresAt, List<String> grantedScopes) {
}
