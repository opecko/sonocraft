package dev.opecko.sonocraft.api.model;

import java.util.List;

public record HelloResult(String apiVersion, String appVersion, String appId, List<String> grantedScopes,
                          boolean signedIn, AppIdentity identity) {
}
