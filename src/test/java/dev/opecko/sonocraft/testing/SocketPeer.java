package dev.opecko.sonocraft.testing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.opecko.sonocraft.api.rpc.LineReader;
import dev.opecko.sonocraft.platform.UnixTransport;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Other end of the Unix socket for RpcConnection tests: the test reads requests and sends responses by hand. */
public final class SocketPeer implements AutoCloseable {
    public final UnixTransport client;
    private final UnixTransport server;
    private final LineReader reader;
    private final Path dir;

    public SocketPeer() throws IOException {
        dir = Files.createTempDirectory("snc-peer");
        Path sock = dir.resolve("s.sock");
        try (ServerSocketChannel ss = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            ss.bind(UnixDomainSocketAddress.of(sock));
            try {
                client = UnixTransport.open(sock);
            } catch (Exception e) {
                throw new IOException(e);
            }
            server = UnixTransport.wrap(ss.accept());
        }
        Files.deleteIfExists(sock);
        reader = new LineReader(server, 4 * 1024 * 1024);
    }

    /** Next message from the client, or null at end of connection. */
    public JsonObject read() throws IOException {
        byte[] line = reader.next();
        return line == null ? null : JsonParser.parseString(new String(line, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public void sendRaw(String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        server.write(b, 0, b.length);
    }

    public void send(String json) throws IOException {
        sendRaw(json + "\n");
    }

    public void closeServerSide() {
        server.close();
    }

    @Override
    public void close() throws IOException {
        client.close();
        server.close();
        Files.deleteIfExists(dir);
    }
}
