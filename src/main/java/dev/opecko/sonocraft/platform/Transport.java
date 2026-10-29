package dev.opecko.sonocraft.platform;

import java.io.Closeable;
import java.io.IOException;

/**
 * Open connection to Sonolith. One thread may read while another writes; reading must not block writing.
 * {@link #close()} may be called from any thread and unblocks pending reads and writes.
 */
public interface Transport extends Closeable {
    /** Reads up to {@code len} bytes; returns -1 at end of connection. */
    int read(byte[] buf, int off, int len) throws IOException;

    /** Writes all bytes. */
    void write(byte[] buf, int off, int len) throws IOException;

    @Override
    void close();
}
