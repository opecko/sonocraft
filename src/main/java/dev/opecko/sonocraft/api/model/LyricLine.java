package dev.opecko.sonocraft.api.model;

import java.util.List;

public record LyricLine(Long startMs, Long endMs, String text, List<LyricWord> words, List<LyricWord> background) {
}
