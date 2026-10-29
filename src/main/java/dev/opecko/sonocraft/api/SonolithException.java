package dev.opecko.sonocraft.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Sonolith error response (JSON-RPC error). Never relies on `message`, only on the code and `data`. */
public final class SonolithException extends Exception {
    private final ErrorCode error;
    private final int code;
    private final String reason;
    private final JsonObject data;

    public SonolithException(int code, String name, JsonObject data, String method) {
        super(method + ": " + (name != null ? name : code) + reasonSuffix(data));
        this.code = code;
        this.error = ErrorCode.of(code, name);
        this.data = data != null ? data : new JsonObject();
        this.reason = string(this.data, "reason");
    }

    /** Locally created PermissionDenied (a method without a granted scope is never sent). */
    public static SonolithException permissionDenied(String method, Scope scope) {
        JsonObject data = new JsonObject();
        data.addProperty("name", ErrorCode.PERMISSION_DENIED.wireName());
        data.addProperty("scope", scope.wire());
        return new SonolithException(ErrorCode.PERMISSION_DENIED.code(), ErrorCode.PERMISSION_DENIED.wireName(),
                data, method);
    }

    /** From the JSON-RPC `error` object. */
    public static SonolithException fromError(JsonObject error, String method) {
        int code = error.has("code") && error.get("code").isJsonPrimitive() ? error.get("code").getAsInt() : 0;
        JsonObject data = error.has("data") && error.get("data").isJsonObject() ? error.getAsJsonObject("data") : null;
        String name = data != null ? string(data, "name") : null;
        return new SonolithException(code, name, data, method);
    }

    public ErrorCode error() {
        return error;
    }

    public int code() {
        return code;
    }

    /** `data.reason`, or null (unknown reasons are treated as null, SPEC §11). */
    public String reason() {
        return reason;
    }

    public JsonObject data() {
        return data;
    }

    public boolean is(ErrorCode c) {
        return error == c;
    }

    /** `data.retryAfterMs`, or -1. */
    public long retryAfterMs() {
        JsonElement e = data.get("retryAfterMs");
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : -1;
    }

    /** `data.requestId` (RegistrationPending), or null. */
    public String requestId() {
        return string(data, "requestId");
    }

    /** `data.scope` (PermissionDenied), or null. */
    public Scope scope() {
        return Scope.fromWire(string(data, "scope"));
    }

    private static String reasonSuffix(JsonObject data) {
        String r = data != null ? string(data, "reason") : null;
        return r != null ? " (" + r + ")" : "";
    }

    static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
