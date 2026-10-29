package dev.opecko.sonocraft.api.model;

import java.util.List;

public record SessionInfo(String appId, List<String> grantedScopes, boolean signedIn, AppIdentity identity) {
}
