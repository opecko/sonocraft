package dev.opecko.sonocraft.platform.token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Token in a file. On POSIX with mode 0600 in a 0700 directory, written atomically (temp file with permissions set
 * at creation, then rename). {@link Codec} allows encrypting the content (DPAPI on Windows).
 */
public final class FileTokenStore implements TokenStore {
    /** Converts the token to file bytes and back. */
    public interface Codec {
        Codec PLAIN = new Codec() {
            @Override
            public byte[] encode(String token) {
                return token.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String decode(byte[] data) {
                return new String(data, StandardCharsets.UTF_8).trim();
            }
        };

        byte[] encode(String token) throws TokenStoreException;

        String decode(byte[] data) throws TokenStoreException;
    }

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path file;
    private final Codec codec;
    private final String name;

    public FileTokenStore(Path file, Codec codec, String name) {
        this.file = file;
        this.codec = codec;
        this.name = name;
    }

    @Override
    public String load() throws TokenStoreException {
        try {
            String token = codec.decode(Files.readAllBytes(file));
            return token.isEmpty() ? null : token;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new TokenStoreException("cannot read " + file, e);
        }
    }

    @Override
    public void save(String token) throws TokenStoreException {
        byte[] data = codec.encode(token);
        Path dir = file.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            if (POSIX) {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                FileAttribute<?> mode = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
                tmp = Files.createTempFile(dir, ".token", ".tmp", mode);
            } else {
                Files.createDirectories(dir);
                tmp = Files.createTempFile(dir, ".token", ".tmp");
            }
            Files.write(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
        } catch (IOException e) {
            throw new TokenStoreException("cannot write " + file, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void delete() throws TokenStoreException {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new TokenStoreException("cannot delete " + file, e);
        }
    }

    @Override
    public String describe() {
        return name + " (" + file + ")";
    }
}
