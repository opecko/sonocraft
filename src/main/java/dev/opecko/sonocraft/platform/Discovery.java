package dev.opecko.sonocraft.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.NotAvailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * Locating Sonolith (SPEC §3.1–3.3).
 *
 * <ol>
 *   <li>Read {@code discovery.json} and take {@code path} from it.
 *   <li>Linux only: without a discovery file, try the default socket path.
 *   <li>Otherwise Sonolith is not available.
 * </ol>
 */
public final class Discovery {
    private final Os os;
    private final Function<String, String> env;
    private final Path tmp;

    public Discovery() {
        this(Os.current(), System::getenv, Path.of("/tmp"));
    }

    /** For tests: custom OS, environment and directory for the {@code /tmp} fallback. */
    public Discovery(Os os, Function<String, String> env, Path tmp) {
        this.os = os;
        this.env = env;
        this.tmp = tmp;
    }

    public Endpoint find() throws NotAvailableException {
        Path file = discoveryFile();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (root.isJsonObject() && string(root.getAsJsonObject(), "path") != null) {
                JsonObject o = root.getAsJsonObject();
                String transport = string(o, "transport");
                if (transport == null) {
                    transport = os == Os.WINDOWS ? "pipe" : "unix";
                }
                JsonElement pid = o.get("pid");
                Long pidValue = pid != null && pid.isJsonPrimitive() && pid.getAsJsonPrimitive().isNumber()
                        ? pid.getAsLong() : null;
                return new Endpoint(transport, string(o, "path"), pidValue, string(o, "apiVersion"),
                        string(o, "appVersion"));
            }
        } catch (NoSuchFileException e) {
            // falls through to the fallback below
        } catch (IOException | RuntimeException e) {
            if (os == Os.WINDOWS) {
                throw new NotAvailableException("unreadable discovery.json", e);
            }
        }
        if (os == Os.WINDOWS) {
            // on Windows the client must not build the pipe name itself (SPEC §3.2)
            throw new NotAvailableException("no discovery.json");
        }
        return new Endpoint("unix", runtimeDir().resolve("api.sock").toString(), null, null, null);
    }

    Path discoveryFile() throws NotAvailableException {
        if (os == Os.WINDOWS) {
            String local = env.apply("LOCALAPPDATA");
            if (local == null || local.isEmpty()) {
                throw new NotAvailableException("LOCALAPPDATA is not set");
            }
            return Path.of(local, "Sonolith", "api", "discovery.json");
        }
        return runtimeDir().resolve("discovery.json");
    }

    /** {@code $XDG_RUNTIME_DIR/sonolith}, or {@code /tmp/sonolith-<uid>} after checking that only we own it. */
    Path runtimeDir() throws NotAvailableException {
        String xdg = env.apply("XDG_RUNTIME_DIR");
        if (xdg != null && !xdg.isEmpty()) {
            return Path.of(xdg, "sonolith");
        }
        int uid = currentUid();
        Path dir = tmp.resolve("sonolith-" + uid);
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return dir;
        }
        try {
            Map<String, Object> attrs = Files.readAttributes(dir, "unix:uid,mode", LinkOption.NOFOLLOW_LINKS);
            int owner = (Integer) attrs.get("uid");
            int mode = (Integer) attrs.get("mode");
            if (owner != uid || (mode & 0777) != 0700 || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                throw new NotAvailableException(dir + " is not private to this user; refusing to connect");
            }
        } catch (IOException | UnsupportedOperationException e) {
            throw new NotAvailableException("cannot check " + dir, e);
        }
        return dir;
    }

    private static int currentUid() throws NotAvailableException {
        try {
            return (Integer) Files.getAttribute(Path.of("/proc/self"), "unix:uid");
        } catch (IOException | UnsupportedOperationException e) {
            throw new NotAvailableException("cannot determine the user id", e);
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
