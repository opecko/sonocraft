package dev.opecko.sonocraft.platform;

/**
 * Where Sonolith listens. {@code transport} is "unix" or "pipe", {@code pid} is null without a discovery file.
 */
public record Endpoint(String transport, String path, Long pid, String apiVersion, String appVersion) {
}
