package dev.opecko.sonocraft.platform.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** DPAPI token storage, on Windows only (CI runs it on windows-latest). */
@EnabledOnOs(OS.WINDOWS)
class DpapiWindowsTest {
    @TempDir
    Path dir;

    @Test
    void encryptsAndDecrypts() throws Exception {
        DpapiCodec codec = new DpapiCodec();
        byte[] blob = codec.encode("snl_secret_token");
        assertFalse(new String(blob, StandardCharsets.ISO_8859_1).contains("snl_secret_token"), "token not encrypted");
        assertEquals("snl_secret_token", codec.decode(blob));
    }

    @Test
    void fileStoreRoundTrip() throws Exception {
        Path file = dir.resolve("Sonocraft/token.dpapi");
        FileTokenStore store = new FileTokenStore(file, new DpapiCodec(), "DPAPI file");
        assertNull(store.load());
        store.save("snl_a");
        assertFalse(Files.readString(file, StandardCharsets.ISO_8859_1).contains("snl_a"));
        assertEquals("snl_a", store.load());
        store.delete();
        assertNull(store.load());
    }
}
