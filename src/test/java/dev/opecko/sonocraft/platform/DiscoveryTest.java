package dev.opecko.sonocraft.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.opecko.sonocraft.api.NotAvailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryTest {
    @TempDir
    Path tmp;

    private static int uid() throws IOException {
        return (Integer) Files.getAttribute(Path.of("/proc/self"), "unix:uid");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void readsDiscoveryFileAndIgnoresUnknownFields() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("run/sonolith"));
        Files.writeString(dir.resolve("discovery.json"), """
                {"apiVersion":"1.0.0","appVersion":"2.4.0","transport":"unix","path":"/x/api.sock","pid":4242,
                 "startedAt":1793021000000,"futureField":[1]}""");
        Endpoint e = new Discovery(Os.LINUX, Map.of("XDG_RUNTIME_DIR", tmp.resolve("run").toString())::get, tmp).find();
        assertEquals(new Endpoint("unix", "/x/api.sock", 4242L, "1.0.0", "2.4.0"), e);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void fallsBackToDefaultSocketWithoutDiscovery() throws Exception {
        Endpoint e = new Discovery(Os.LINUX, Map.of("XDG_RUNTIME_DIR", "/run/user/7")::get, tmp).find();
        assertEquals("/run/user/7/sonolith/api.sock", e.path());
        assertNull(e.pid());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void tmpFallbackRequiresPrivateDirectory() throws Exception {
        Path dir = tmp.resolve("sonolith-" + uid());
        Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Discovery d = new Discovery(Os.LINUX, Map.<String, String>of()::get, tmp);
        assertEquals(dir.resolve("api.sock").toString(), d.find().path());

        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThrows(NotAvailableException.class, d::find);
    }

    @Test
    void windowsNeverBuildsThePipeName() {
        Discovery d = new Discovery(Os.WINDOWS, Map.of("LOCALAPPDATA", tmp.toString())::get, tmp);
        assertThrows(NotAvailableException.class, d::find);
    }

    @Test
    void windowsReadsPipeFromDiscovery() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("Sonolith/api"));
        Files.writeString(dir.resolve("discovery.json"), """
                {"apiVersion":"1.0.0","appVersion":"2.4.0","transport":"pipe","path":"\\\\\\\\.\\\\pipe\\\\sonolith-api-alex","pid":77}""");
        Endpoint e = new Discovery(Os.WINDOWS, Map.of("LOCALAPPDATA", tmp.toString())::get, tmp).find();
        assertEquals("\\\\.\\pipe\\sonolith-api-alex", e.path());
        assertEquals(77L, e.pid());
    }
}
