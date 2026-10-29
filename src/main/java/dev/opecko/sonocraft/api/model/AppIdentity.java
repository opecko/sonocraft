package dev.opecko.sonocraft.api.model;

public record AppIdentity(String kind, String key, String executable, String interpreter, String script,
                          String appImage, String flatpakId) {
}
