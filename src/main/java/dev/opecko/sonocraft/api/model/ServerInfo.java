package dev.opecko.sonocraft.api.model;

import java.util.List;

public record ServerInfo(String apiVersion, String appVersion, String platform, List<String> methods,
                         List<String> topics, SessionInfo session) {
}
