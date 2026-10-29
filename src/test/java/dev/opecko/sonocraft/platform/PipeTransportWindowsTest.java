package dev.opecko.sonocraft.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import dev.opecko.sonocraft.api.NotAvailableException;
import dev.opecko.sonocraft.api.rpc.RpcConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * {@link PipeTransport} against a real named pipe server made with JNA, on Windows only (CI runs it on
 * windows-latest). The server side uses a plain synchronous handle; the client is what is under test.
 */
@EnabledOnOs(OS.WINDOWS)
class PipeTransportWindowsTest {
    private static final Kernel32 K = Kernel32.INSTANCE;

    private String name;
    private HANDLE server;
    private CompletableFuture<Void> accepted;

    @BeforeEach
    void createServer() {
        name = "\\\\.\\pipe\\sonocraft-test-" + UUID.randomUUID();
        server = K.CreateNamedPipe(name, WinBase.PIPE_ACCESS_DUPLEX,
                WinBase.PIPE_TYPE_BYTE | WinBase.PIPE_READMODE_BYTE | WinBase.PIPE_WAIT, 1, 65536, 65536, 0, null);
        assertNotEquals(WinBase.INVALID_HANDLE_VALUE, server, "CreateNamedPipe failed: " + K.GetLastError());
        // ConnectNamedPipe returns once a client is there (or says it already is)
        accepted = CompletableFuture.runAsync(() -> K.ConnectNamedPipe(server, null));
    }

    @AfterEach
    void closeServer() {
        if (server != null) {
            K.CloseHandle(server);
        }
    }

    private PipeTransport connect() throws NotAvailableException {
        PipeTransport t = PipeTransport.open(name, ProcessHandle.current().pid());
        accepted.join();
        return t;
    }

    private void serverWrite(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        IntByReference written = new IntByReference();
        assertTrue(K.WriteFile(server, b, b.length, written, null), "server WriteFile failed: " + K.GetLastError());
    }

    private String serverReadLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] one = new byte[1];
        IntByReference read = new IntByReference();
        while (K.ReadFile(server, one, 1, read, null) && read.getValue() == 1) {
            if (one[0] == '\n') {
                return out.toString(StandardCharsets.UTF_8);
            }
            out.write(one[0]);
        }
        throw new AssertionError("server ReadFile failed: " + K.GetLastError());
    }

    private static String readLine(PipeTransport t) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1];
        while (t.read(buf, 0, 1) == 1) {
            if (buf[0] == '\n') {
                return out.toString(StandardCharsets.UTF_8);
            }
            out.write(buf[0]);
        }
        return null;
    }

    @Test
    void writesWhileAnotherThreadIsBlockedReading() throws Exception {
        try (PipeTransport client = connect()) {
            // the reader blocks first: with a synchronous handle this would also block every write
            CompletableFuture<String> reply = CompletableFuture.supplyAsync(() -> {
                try {
                    return readLine(client);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(200);
            byte[] ping = "ping\n".getBytes(StandardCharsets.UTF_8);
            CompletableFuture.runAsync(() -> {
                try {
                    client.write(ping, 0, ping.length);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).get(5, TimeUnit.SECONDS);
            assertEquals("ping", serverReadLine());
            serverWrite("pong\n");
            assertEquals("pong", reply.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeUnblocksAPendingRead() throws Exception {
        PipeTransport client = connect();
        CompletableFuture<Object> read = CompletableFuture.supplyAsync(() -> {
            try {
                return client.read(new byte[16], 0, 16);
            } catch (IOException e) {
                return e;
            }
        });
        Thread.sleep(200);
        client.close();
        Object result = read.get(5, TimeUnit.SECONDS);
        assertTrue(result instanceof IOException || Integer.valueOf(-1).equals(result), "read ended with " + result);
    }

    @Test
    void serverClosingEndsTheStream() throws Exception {
        try (PipeTransport client = connect()) {
            serverWrite("last\n");
            K.CloseHandle(server);
            server = null;
            assertEquals("last", readLine(client));
            assertEquals(-1, client.read(new byte[4], 0, 4));
        }
    }

    @Test
    void jsonRpcWorksOverThePipe() throws Exception {
        try (PipeTransport client = connect();
             RpcConnection rpc = new RpcConnection(client, (m, p) -> { }).start()) {
            CompletableFuture<com.google.gson.JsonElement> call = rpc.call("app.info", null);
            String request = serverReadLine();
            assertTrue(request.contains("\"app.info\""), request);
            serverWrite("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":42}\n");
            assertEquals(42, call.get(5, TimeUnit.SECONDS).getAsInt());
        }
    }

    @Test
    void rejectsAPipeServedByAnotherProcess() {
        assertThrows(NotAvailableException.class, () -> PipeTransport.open(name, ProcessHandle.current().pid() + 1));
    }

    @Test
    void missingPipeIsNotAvailable() throws Exception {
        assertThrows(NotAvailableException.class,
                () -> PipeTransport.open("\\\\.\\pipe\\sonocraft-missing-" + UUID.randomUUID(), null));
        // release the server's ConnectNamedPipe, which would otherwise hold a pool thread
        connect().close();
    }
}
