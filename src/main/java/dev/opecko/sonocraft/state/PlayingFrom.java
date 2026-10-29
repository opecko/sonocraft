package dev.opecko.sonocraft.state;

import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.session.SessionListener;

/**
 * What the queue was started from ("Playing from Currents"). The API doesn't say, so it is only known when
 * Sonocraft started it; a queue replaced from elsewhere (Sonolith itself) clears it.
 */
public final class PlayingFrom implements SessionListener {
    /** A "replaced" queue change this soon after our own play call is ours. */
    private static final long OWN_CHANGE_MS = 5000;

    private volatile String title;
    private volatile long setAt;

    /** Sonocraft started playback of this collection; null for a single song. */
    public void set(String title) {
        this.title = title;
        this.setAt = System.currentTimeMillis();
    }

    public String get() {
        return title;
    }

    @Override
    public void onEvent(String method, JsonObject params) {
        if (method.equals("queue.changed") && params.has("reason") && params.get("reason").isJsonPrimitive()
                && "replaced".equals(params.get("reason").getAsString())
                && System.currentTimeMillis() - setAt > OWN_CHANGE_MS) {
            title = null;
        }
    }
}
