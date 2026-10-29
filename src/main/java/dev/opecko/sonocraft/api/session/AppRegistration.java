package dev.opecko.sonocraft.api.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.opecko.sonocraft.api.ApiMethods;
import dev.opecko.sonocraft.api.Scope;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data for app.register (SPEC §6). Texts are single-line without control characters; the icon is a PNG data URI of
 * up to 65 536 characters, or null.
 */
public record AppRegistration(String appId, String name, String description, String publisher, String homepage,
                              String icon, Map<Scope, ScopeSpec> scopes) {
    public record ScopeSpec(boolean required, String reason) {
    }

    public AppRegistration {
        scopes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(scopes));
        for (String text : new String[] {appId, name, description, publisher}) {
            requireSingleLine(text);
        }
        scopes.values().forEach(s -> requireSingleLine(s.reason()));
        if (icon != null && icon.length() > 65_536) {
            throw new IllegalArgumentException("icon over 65536 characters");
        }
    }

    private static void requireSingleLine(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty registration text");
        }
        for (int i = 0; i < text.length(); i++) {
            int type = Character.getType(text.charAt(i));
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("control or formatting character in: " + text);
            }
        }
    }

    public JsonObject registerParams() {
        JsonObject o = new JsonObject();
        o.addProperty("appId", appId);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("publisher", publisher);
        if (homepage != null) {
            o.addProperty("homepage", homepage);
        }
        if (icon != null) {
            o.addProperty("icon", icon);
        }
        o.add("scopes", scopeRequests(scopes.keySet()));
        o.addProperty("apiVersion", ApiMethods.API_VERSION);
        return o;
    }

    /** ScopeRequest[] for the given scopes (with reasons from the registration). */
    public JsonArray scopeRequests(Collection<Scope> wanted) {
        JsonArray arr = new JsonArray();
        for (Scope s : wanted) {
            ScopeSpec spec = scopes.get(s);
            if (spec == null) {
                throw new IllegalArgumentException("scope not declared in the registration: " + s);
            }
            JsonObject r = new JsonObject();
            r.addProperty("scope", s.wire());
            r.addProperty("required", spec.required());
            r.addProperty("reason", spec.reason());
            arr.add(r);
        }
        return arr;
    }
}
