package dev.opecko.sonocraft.api.rpc;

import dev.opecko.sonocraft.platform.Transport;
import java.io.IOException;
import java.util.Arrays;

/**
 * Reads `\n`-delimited messages (SPEC §4.2). `\r` before `\n` is dropped, empty lines are skipped and a message
 * longer than {@code maxBytes} ends reading with {@link MessageTooLargeException}.
 */
public final class LineReader {
    private final Transport transport;
    private final int maxBytes;
    private final byte[] chunk = new byte[64 * 1024];
    private int chunkPos;
    private int chunkLen;
    private byte[] line = new byte[8 * 1024];
    private int lineLen;

    public LineReader(Transport transport, int maxBytes) {
        this.transport = transport;
        this.maxBytes = maxBytes;
    }

    /** Next non-empty line without `\r\n`, or null at end of connection (an incomplete last line is dropped). */
    public byte[] next() throws IOException {
        while (true) {
            if (chunkPos == chunkLen) {
                int n = transport.read(chunk, 0, chunk.length);
                if (n < 0) {
                    return null;
                }
                chunkPos = 0;
                chunkLen = n;
                continue;
            }
            int nl = -1;
            for (int i = chunkPos; i < chunkLen; i++) {
                if (chunk[i] == '\n') {
                    nl = i;
                    break;
                }
            }
            int end = nl >= 0 ? nl : chunkLen;
            append(chunkPos, end - chunkPos);
            chunkPos = nl >= 0 ? nl + 1 : chunkLen;
            if (nl < 0) {
                continue;
            }
            int len = lineLen;
            if (len > 0 && line[len - 1] == '\r') {
                len--;
            }
            lineLen = 0;
            if (len > maxBytes) {
                throw new MessageTooLargeException(len, maxBytes);
            }
            if (len == 0) {
                continue;
            }
            return Arrays.copyOf(line, len);
        }
    }

    private void append(int from, int count) throws MessageTooLargeException {
        // +1 for a possible \r, which doesn't count toward the limit
        if (lineLen + count > maxBytes + 1) {
            throw new MessageTooLargeException(lineLen + count, maxBytes);
        }
        if (lineLen + count > line.length) {
            line = Arrays.copyOf(line, Math.min(Math.max(line.length * 2, lineLen + count), maxBytes + 1));
        }
        System.arraycopy(chunk, from, line, lineLen, count);
        lineLen += count;
    }

    public static final class MessageTooLargeException extends IOException {
        public MessageTooLargeException(int size, int max) {
            super("message of at least " + size + " bytes exceeds the limit of " + max);
        }
    }
}
