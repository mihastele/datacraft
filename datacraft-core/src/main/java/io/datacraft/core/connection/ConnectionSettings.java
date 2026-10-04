/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

import java.util.Objects;

/** Non-secret connection settings. Timeout applies to connect, socket, and metadata operations. */
public record ConnectionSettings(DatabaseKind kind, String host, int port, String database, String username,
        Environment environment, TlsMode tlsMode, int timeoutSeconds) implements ConnectionProfile {
    /** Convenience constructor for the original PostgreSQL client API. */
    public ConnectionSettings(String host, int port, String database, String username,
            Environment environment, TlsMode tlsMode, int timeoutSeconds) {
        this(DatabaseKind.POSTGRESQL, host, port, database, username, environment, tlsMode, timeoutSeconds);
    }
    public ConnectionSettings {
        Objects.requireNonNull(kind, "kind");
        if (kind == DatabaseKind.SQLITE) throw new IllegalArgumentException("SQLite requires file settings.");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(tlsMode, "tlsMode");
        if (!host.matches("[a-zA-Z0-9._:\\[\\]-]+")) {
            throw new IllegalArgumentException("Host must be a hostname or IP address, not a URL.");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port.");
        if (database.isBlank() || database.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid database name.");
        }
        if (username.isBlank() || username.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid username.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("Timeout must be between 1 and 300 seconds.");
        }
    }
}
