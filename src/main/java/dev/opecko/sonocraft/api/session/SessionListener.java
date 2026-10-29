package dev.opecko.sonocraft.api.session;

import com.google.gson.JsonObject;

/**
 * Session listener. All methods are called on the Sonocraft-Events thread (never on the reader or game
 * thread); hand off to MC state via {@code Minecraft#execute} or a thread-safe store.
 */
public interface SessionListener {
    default void onStatus(SessionStatus status) {
    }

    /** Every notification from Sonolith, including unknown ones (the listener should ignore those). */
    default void onEvent(String method, JsonObject params) {
    }

    default void onNotice(Notice notice) {
    }
}
