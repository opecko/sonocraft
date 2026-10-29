package dev.opecko.sonocraft.api.model;

import java.util.List;

public record Lyrics(String trackId, LyricsTier tier, String provider, String providerName, String attribution,
                     List<LyricLine> lines) {
}
