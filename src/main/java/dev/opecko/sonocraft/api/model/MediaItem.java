package dev.opecko.sonocraft.api.model;

/** Playable queue or playlist item: a track or an episode. */
public sealed interface MediaItem extends Item permits Track, Episode {
    Long durationMs();
}
