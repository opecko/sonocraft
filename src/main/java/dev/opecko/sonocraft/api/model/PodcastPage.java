package dev.opecko.sonocraft.api.model;

import java.util.List;

public record PodcastPage(PodcastDetail podcast, List<Episode> items, String nextCursor) {
}
