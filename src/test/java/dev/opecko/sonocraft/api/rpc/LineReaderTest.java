package dev.opecko.sonocraft.api.rpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.opecko.sonocraft.platform.Transport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.junit.jupiter.api.Test;

class LineReaderTest {
    /** Transport that returns predefined chunks of data. */
    private static Transport chunks(byte[]... parts) {
        Deque<byte[]> queue = new ArrayDeque<>(Arrays.asList(parts));
        return new Transport() {
            @Override
            public int read(byte[] buf, int off, int len) {
                byte[] next = queue.poll();
                if (next == null) {
                    return -1;
                }
                int n = Math.min(len, next.length);
                System.arraycopy(next, 0, buf, off, n);
                if (n < next.length) {
                    queue.push(Arrays.copyOfRange(next, n, next.length));
                }
                return n;
            }

            @Override
            public void write(byte[] buf, int off, int len) {
            }

            @Override
            public void close() {
            }
        };
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void splitsLinesStripsCrAndSkipsEmpty() throws IOException {
        LineReader r = new LineReader(chunks(b("{\"a\":1}\r\n\n\r\n{\"b\""), b(":2}\n"), b("{\"c\":3}\n")), 100);
        assertArrayEquals(b("{\"a\":1}"), r.next());
        assertArrayEquals(b("{\"b\":2}"), r.next());
        assertArrayEquals(b("{\"c\":3}"), r.next());
        assertNull(r.next());
    }

    @Test
    void dropsIncompleteLastLine() throws IOException {
        LineReader r = new LineReader(chunks(b("{}\n{\"cut"), new byte[0]), 100);
        assertArrayEquals(b("{}"), r.next());
        assertNull(r.next());
    }

    @Test
    void acceptsExactlyMaxAndRejectsMore() throws IOException {
        byte[] max = new byte[1000];
        Arrays.fill(max, (byte) 'x');
        LineReader ok = new LineReader(chunks(max, b("\r\n")), 1000);
        assertArrayEquals(max, ok.next());

        byte[] over = new byte[1001];
        Arrays.fill(over, (byte) 'x');
        LineReader bad = new LineReader(chunks(over, b("\n")), 1000);
        assertThrows(LineReader.MessageTooLargeException.class, bad::next);
    }

    @Test
    void rejectsOversizedLineBeforeItEnds() {
        byte[] big = new byte[5000];
        Arrays.fill(big, (byte) 'x');
        // no \n: the reader must not wait for the end and buffer data forever
        LineReader r = new LineReader(chunks(big, big, big), 1000);
        assertThrows(LineReader.MessageTooLargeException.class, r::next);
    }
}
