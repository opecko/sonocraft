package dev.opecko.sonocraft.api.model;

import java.util.List;

public record ScopesChanged(List<String> grantedScopes, List<String> added, List<String> removed) {
}
