package dev.opecko.sonocraft.platform;

import dev.opecko.sonocraft.api.NotAvailableException;
import java.nio.file.Path;

/** Opens connections to Sonolith. Every failure is a {@link NotAvailableException}. */
@FunctionalInterface
public interface Connector {
    Transport open() throws NotAvailableException;

    /** Discovery and transport for the current platform. */
    static Connector system() {
        Discovery discovery = new Discovery();
        return () -> {
            Endpoint e = discovery.find();
            return switch (e.transport()) {
                case "unix" -> {
                    if (Os.current() == Os.WINDOWS) {
                        throw new NotAvailableException("a Unix socket endpoint on Windows");
                    }
                    yield UnixTransport.open(Path.of(e.path()));
                }
                case "pipe" -> {
                    if (Os.current() != Os.WINDOWS) {
                        throw new NotAvailableException("a named pipe endpoint outside Windows");
                    }
                    yield PipeTransport.open(e.path(), e.pid());
                }
                default -> throw new NotAvailableException("unknown transport " + e.transport());
            };
        };
    }
}
