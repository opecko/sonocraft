package dev.opecko.sonocraft.api.model;

public record RegistrationPending(String status, String requestId, long expiresAt, AppIdentity identity) {
}
