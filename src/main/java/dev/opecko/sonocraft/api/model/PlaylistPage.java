package dev.opecko.sonocraft.api.model;

import java.util.List;

public record PlaylistPage(PlaylistDetail playlist, List<PlaylistEntry> items, String nextCursor) {
}
