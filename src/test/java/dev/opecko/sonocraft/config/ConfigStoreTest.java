package dev.opecko.sonocraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
    @TempDir
    Path dir;

    @Test
    void missingFileGivesDefaultsAndSaveRoundTrips() {
        ConfigStore store = new ConfigStore(dir.resolve("config/sonocraft.json"));
        SonocraftConfig c = store.load();
        assertTrue(c.hud.enabled);
        c.hud.enabled = false;
        c.hud.connectionStatus = SonocraftConfig.StatusVisibility.ALWAYS;
        store.save();

        SonocraftConfig again = new ConfigStore(dir.resolve("config/sonocraft.json")).load();
        assertFalse(again.hud.enabled);
        assertEquals(SonocraftConfig.StatusVisibility.ALWAYS, again.hud.connectionStatus);
    }

    @Test
    void partialFileKeepsDefaultsForMissingParts() throws Exception {
        Path file = dir.resolve("sonocraft.json");
        Files.writeString(file, "{\"hud\":{\"enabled\":false,\"connectionStatus\":\"SOMETHING_NEW\"},\"future\":1}");
        SonocraftConfig c = new ConfigStore(file).load();
        assertFalse(c.hud.enabled);
        assertEquals(SonocraftConfig.StatusVisibility.ON_ERROR, c.hud.connectionStatus);
        assertTrue(c.controls.feedback);
        assertTrue(c.connection.toasts);
    }

    @Test
    void brokenFileIsMovedAsideAndDefaultsUsed() throws Exception {
        Path file = dir.resolve("sonocraft.json");
        Files.writeString(file, "{ not json");
        SonocraftConfig c = new ConfigStore(file).load();
        assertTrue(c.hud.enabled);
        assertTrue(Files.exists(dir.resolve("sonocraft.json.broken")));
        assertFalse(Files.exists(file));
    }
}
