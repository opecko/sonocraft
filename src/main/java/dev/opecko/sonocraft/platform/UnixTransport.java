package dev.opecko.sonocraft.platform;

import dev.opecko.sonocraft.api.NotAvailableException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/**
 * Unix domain socket via {@code java.nio} (no native libraries). SocketChannel has separate read and write locks,
 * so a blocking read doesn't hold up a write. The socket file is never deleted (SPEC §3.5).
 */
public final class UnixTransport implements Transport {
    private final SocketChannel channel;

    private UnixTransport(SocketChannel channel) {
        this.channel = channel;
    }

    public static UnixTransport open(Path path) throws NotAvailableException {
        SocketChannel ch = null;
        try {
            ch = SocketChannel.open(StandardProtocolFamily.UNIX);
            ch.connect(UnixDomainSocketAddress.of(path));
            return new UnixTransport(ch);
        } catch (IOException | UnsupportedOperationException e) {
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
            throw new NotAvailableException("cannot connect to " + path + ": " + e.getMessage(), e);
        }
    }

    /** For tests and the fake server: wraps an already connected channel. */
    public static UnixTransport wrap(SocketChannel channel) {
        return new UnixTransport(channel);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n;
        do {
            n = channel.read(ByteBuffer.wrap(buf, off, len));
        } while (n == 0);
        return n;
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(buf, off, len);
        while (b.hasRemaining()) {
            channel.write(b);
        }
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {
            // ignore
        }
    }
}
