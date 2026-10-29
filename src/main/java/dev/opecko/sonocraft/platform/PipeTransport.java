package dev.opecko.sonocraft.platform;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import dev.opecko.sonocraft.api.NotAvailableException;
import java.io.IOException;

/**
 * Windows named pipe with overlapped I/O via JNA.
 *
 * <p>A synchronous handle (RandomAccessFile) serializes reads and writes, so a blocking read would block all
 * writes. Hence {@code FILE_FLAG_OVERLAPPED} and a separate OVERLAPPED, event and native buffer per direction.
 * OVERLAPPED is passed as a {@link Pointer}, not a JNA Structure: a Structure would overwrite the native fields
 * with Java values before every call while the kernel is writing to them.
 *
 * <p>Before sending the token, verifies that the pipe server is the process from {@code discovery.json} (SPEC §3.2, §14).
 */
public final class PipeTransport implements Transport {
    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE CreateFile(String name, int access, int share, Pointer security, int disposition, int flags,
                          HANDLE template);

        boolean WaitNamedPipe(String name, int timeoutMs);

        boolean GetNamedPipeServerProcessId(HANDLE pipe, IntByReference pid);

        HANDLE CreateEvent(Pointer security, boolean manualReset, boolean initialState, String name);

        boolean ReadFile(HANDLE file, Pointer buffer, int toRead, Pointer read, Pointer overlapped);

        boolean WriteFile(HANDLE file, Pointer buffer, int toWrite, Pointer written, Pointer overlapped);

        boolean GetOverlappedResult(HANDLE file, Pointer overlapped, IntByReference transferred, boolean wait);

        boolean CancelIoEx(HANDLE file, Pointer overlapped);

        boolean CloseHandle(HANDLE handle);
    }

    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int ERROR_BROKEN_PIPE = 109;
    private static final int ERROR_PIPE_BUSY = 231;
    private static final int ERROR_MORE_DATA = 234;
    private static final int ERROR_PIPE_NOT_CONNECTED = 233;
    private static final int ERROR_OPERATION_ABORTED = 995;
    private static final int ERROR_IO_PENDING = 997;
    private static final int BUFFER_SIZE = 64 * 1024;

    /** OVERLAPPED: Internal, InternalHigh (ULONG_PTR), Offset, OffsetHigh (DWORD), hEvent (HANDLE). */
    private static final int OVERLAPPED_EVENT_OFFSET = 2 * Native.POINTER_SIZE + 8;
    private static final int OVERLAPPED_SIZE = OVERLAPPED_EVENT_OFFSET + Native.POINTER_SIZE;

    private final HANDLE pipe;
    private final Op reader;
    private final Op writer;
    private final Object lock = new Object();
    private int activeOps;
    private boolean closed;
    private boolean handlesClosed;

    /** State of one direction: OVERLAPPED, event and buffer live as long as the transport. */
    private static final class Op {
        final Memory overlapped = new Memory(OVERLAPPED_SIZE);
        final Memory buffer = new Memory(BUFFER_SIZE);
        final IntByReference transferred = new IntByReference();
        final HANDLE event;

        Op() throws NotAvailableException {
            event = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
            if (event == null) {
                throw new NotAvailableException("CreateEvent failed: " + Native.getLastError());
            }
        }
    }

    private PipeTransport(HANDLE pipe, Op reader, Op writer) {
        this.pipe = pipe;
        this.reader = reader;
        this.writer = writer;
    }

    public static PipeTransport open(String name, Long expectedPid) throws NotAvailableException {
        Kernel32 k = Kernel32.INSTANCE;
        HANDLE h = createFile(name);
        if (h == null) {
            int err = Native.getLastError();
            if (err == ERROR_PIPE_BUSY && k.WaitNamedPipe(name, 2000)) {
                h = createFile(name);
            }
            if (h == null) {
                int last = Native.getLastError();
                throw new NotAvailableException(last == ERROR_FILE_NOT_FOUND
                        ? "the pipe does not exist" : "cannot open the pipe (error " + last + ")");
            }
        }
        try {
            if (expectedPid != null) {
                IntByReference pid = new IntByReference();
                if (k.GetNamedPipeServerProcessId(h, pid) && Integer.toUnsignedLong(pid.getValue()) != expectedPid) {
                    throw new NotAvailableException("the pipe belongs to process "
                            + Integer.toUnsignedLong(pid.getValue()) + ", not Sonolith (" + expectedPid + ")");
                }
            }
            return new PipeTransport(h, new Op(), new Op());
        } catch (NotAvailableException | RuntimeException e) {
            k.CloseHandle(h);
            throw e;
        }
    }

    private static HANDLE createFile(String name) {
        HANDLE h = Kernel32.INSTANCE.CreateFile(name, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null,
                WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OVERLAPPED, null);
        return h == null || WinBase.INVALID_HANDLE_VALUE.equals(h) ? null : h;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        while (true) {
            int n = transfer(reader, true, Math.min(len, BUFFER_SIZE));
            if (n < 0) {
                return -1;
            }
            if (n > 0) {
                reader.buffer.read(0, buf, off, n);
                return n;
            }
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int chunk = Math.min(len, BUFFER_SIZE);
            writer.buffer.write(0, buf, off, chunk);
            int n = transfer(writer, false, chunk);
            if (n <= 0) {
                throw new IOException("pipe closed");
            }
            off += n;
            len -= n;
        }
    }

    /** One overlapped operation; returns the byte count, or -1 at end of connection. */
    private int transfer(Op op, boolean read, int len) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException("pipe closed");
            }
            activeOps++;
        }
        try {
            Kernel32 k = Kernel32.INSTANCE;
            op.overlapped.clear(OVERLAPPED_SIZE);
            op.overlapped.setPointer(OVERLAPPED_EVENT_OFFSET, op.event.getPointer());
            boolean ok = read
                    ? k.ReadFile(pipe, op.buffer, len, null, op.overlapped)
                    : k.WriteFile(pipe, op.buffer, len, null, op.overlapped);
            if (!ok) {
                int err = Native.getLastError();
                if (err != ERROR_IO_PENDING && err != ERROR_MORE_DATA) {
                    return failed(err, read);
                }
            }
            if (!k.GetOverlappedResult(pipe, op.overlapped, op.transferred, true)) {
                int err = Native.getLastError();
                if (err != ERROR_MORE_DATA) {
                    return failed(err, read);
                }
            }
            return op.transferred.getValue();
        } finally {
            boolean closeNow;
            synchronized (lock) {
                activeOps--;
                closeNow = closed && activeOps == 0;
            }
            if (closeNow) {
                closeHandles();
            }
        }
    }

    private static int failed(int err, boolean read) throws IOException {
        if (err == ERROR_BROKEN_PIPE || err == ERROR_PIPE_NOT_CONNECTED) {
            if (read) {
                return -1;
            }
            throw new IOException("pipe closed by Sonolith");
        }
        if (err == ERROR_OPERATION_ABORTED) {
            throw new IOException("pipe closed");
        }
        throw new IOException("pipe I/O failed (error " + err + ")");
    }

    @Override
    public void close() {
        boolean closeNow;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            closeNow = activeOps == 0;
        }
        // pending operations end with ERROR_OPERATION_ABORTED; whichever finishes last closes the handle
        Kernel32.INSTANCE.CancelIoEx(pipe, null);
        if (closeNow) {
            closeHandles();
        }
    }

    private void closeHandles() {
        synchronized (lock) {
            if (handlesClosed) {
                return;
            }
            handlesClosed = true;
        }
        Kernel32 k = Kernel32.INSTANCE;
        k.CloseHandle(pipe);
        k.CloseHandle(reader.event);
        k.CloseHandle(writer.event);
    }
}
