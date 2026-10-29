package dev.opecko.sonocraft.platform.token;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Linux Secret Service via {@code secret-tool} (libsecret). The token only goes through stdin/stdout, never in
 * arguments or the environment (SPEC §7.2).
 */
public final class SecretToolTokenStore implements TokenStore {
    private static final String[] ATTRIBUTES = {"application", "dev.opecko.sonocraft", "kind", "sonolith-token"};
    private static final long TIMEOUT_SECONDS = 60; // unlocking the keyring may wait for the user

    private final String executable;

    private SecretToolTokenStore(String executable) {
        this.executable = executable;
    }

    /** A store if {@code secret-tool} is on PATH, otherwise null. */
    public static SecretToolTokenStore findOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            Path p = Path.of(dir, "secret-tool");
            if (Files.isExecutable(p)) {
                return new SecretToolTokenStore(p.toString());
            }
        }
        return null;
    }

    @Override
    public String load() throws TokenStoreException {
        Result r = run("lookup", null);
        if (r.exit == 0) {
            String token = r.stdout.trim();
            return token.isEmpty() ? null : token;
        }
        // exit 1 without an error message = item doesn't exist
        if (r.exit == 1 && r.stderr.isBlank()) {
            return null;
        }
        throw new TokenStoreException("secret-tool lookup failed (" + r.exit + "): " + r.stderr.trim());
    }

    @Override
    public void save(String token) throws TokenStoreException {
        Result r = run("store", token);
        if (r.exit != 0) {
            throw new TokenStoreException("secret-tool store failed (" + r.exit + "): " + r.stderr.trim());
        }
    }

    @Override
    public void delete() throws TokenStoreException {
        Result r = run("clear", null);
        // clear with no matching item returns 1 without a message
        if (r.exit != 0 && !(r.exit == 1 && r.stderr.isBlank())) {
            throw new TokenStoreException("secret-tool clear failed (" + r.exit + "): " + r.stderr.trim());
        }
    }

    @Override
    public String describe() {
        return "Secret Service (secret-tool)";
    }

    private record Result(int exit, String stdout, String stderr) {
    }

    private Result run(String command, String stdin) throws TokenStoreException {
        List<String> cmd = new ArrayList<>(List.of(executable, command));
        if (command.equals("store")) {
            cmd.add("--label=Sonocraft: Sonolith token");
        }
        cmd.addAll(List.of(ATTRIBUTES));
        Process p;
        try {
            p = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new TokenStoreException("cannot run secret-tool", e);
        }
        try {
            CompletableFuture<String> out = drain(p.getInputStream());
            CompletableFuture<String> err = drain(p.getErrorStream());
            try (OutputStream in = p.getOutputStream()) {
                if (stdin != null) {
                    in.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TokenStoreException("secret-tool " + command + " timed out");
            }
            return new Result(p.exitValue(), out.get(5, TimeUnit.SECONDS), err.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenStoreException("interrupted", e);
        } catch (TokenStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenStoreException("secret-tool " + command + " failed", e);
        } finally {
            p.destroyForcibly();
        }
    }

    private static CompletableFuture<String> drain(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }
}
