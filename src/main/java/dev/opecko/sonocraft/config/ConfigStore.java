package dev.opecko.sonocraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.opecko.sonocraft.Sonocraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link SonocraftConfig}. A broken file is kept as {@code sonocraft.json.broken} and replaced by
 * defaults instead of crashing the game. Saving writes a temp file and renames it, so a crash never leaves half a
 * file behind.
 */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private volatile SonocraftConfig config = new SonocraftConfig();

    public ConfigStore(Path file) {
        this.file = file;
    }

    public SonocraftConfig get() {
        return config;
    }

    public SonocraftConfig load() {
        try {
            SonocraftConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SonocraftConfig.class);
            config = loaded != null ? loaded.normalize() : new SonocraftConfig();
        } catch (NoSuchFileException e) {
            config = new SonocraftConfig();
        } catch (IOException | JsonParseException e) {
            Sonocraft.LOGGER.warn("Cannot read {}, using defaults: {}", file, e.getMessage());
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // keep going with defaults; the next save overwrites the file
            }
            config = new SonocraftConfig();
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(config), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Sonocraft.LOGGER.error("Cannot save {}: {}", file, e.getMessage());
        }
    }
}
