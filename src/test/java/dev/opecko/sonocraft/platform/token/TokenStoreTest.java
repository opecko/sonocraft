package dev.opecko.sonocraft.platform.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.opecko.sonocraft.testing.MemoryTokenStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class TokenStoreTest {
    @TempDir
    Path tmp;

    @Test
    void fileStoreRoundTrip() throws Exception {
        FileTokenStore s = new FileTokenStore(tmp.resolve("sonocraft/token"), FileTokenStore.Codec.PLAIN, "file");
        assertNull(s.load());
        s.save("snl_a");
        s.save("snl_b");
        assertEquals("snl_b", s.load());
        s.delete();
        assertNull(s.load());
        s.delete();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void fileStoreIsPrivate() throws Exception {
        Path file = tmp.resolve("sonocraft/token");
        new FileTokenStore(file, FileTokenStore.Codec.PLAIN, "file").save("snl_a");
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent())));
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "no temp files left behind");
        }
    }

    private static final class BrokenStore implements TokenStore {
        @Override
        public String load() throws TokenStoreException {
            throw new TokenStoreException("locked");
        }

        @Override
        public void save(String token) throws TokenStoreException {
            throw new TokenStoreException("locked");
        }

        @Override
        public void delete() throws TokenStoreException {
            throw new TokenStoreException("locked");
        }

        @Override
        public String describe() {
            return "broken";
        }
    }

    @Test
    void fallbackUsesSecondStoreWhenFirstFails() throws Exception {
        MemoryTokenStore file = new MemoryTokenStore(null);
        FallbackTokenStore s = new FallbackTokenStore(new BrokenStore(), file);
        s.save("snl_a");
        assertEquals("snl_a", file.token());
        assertEquals("snl_a", s.load());
    }

    @Test
    void fallbackMovesTokenToPrimaryAndDeletesEverywhere() throws Exception {
        MemoryTokenStore primary = new MemoryTokenStore(null);
        MemoryTokenStore file = new MemoryTokenStore("snl_old");
        FallbackTokenStore s = new FallbackTokenStore(primary, file);
        assertEquals("snl_old", s.load());
        s.save("snl_new");
        assertEquals("snl_new", primary.token());
        assertNull(file.token());
        s.delete();
        assertNull(primary.token());
        assertNull(s.load());
    }
}
