/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

import java.nio.file.Path;
import java.util.Objects;

/** Existing local SQLite file; no raw JDBC URL, create mode, credentials, or TLS. */
public record SqliteConnectionSettings(Path file, Environment environment, int timeoutSeconds)
        implements ConnectionProfile {
    public SqliteConnectionSettings {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Objects.requireNonNull(environment, "environment");
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("Timeout must be between 1 and 300 seconds.");
        }
    }
    @Override public DatabaseKind kind() { return DatabaseKind.SQLITE; }
}
