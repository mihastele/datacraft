/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sqlite;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.sqlite.SQLiteConfig;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;

/** Opens existing files only, with native read-only flags and extension loading disabled. */
public final class SqliteAdapter implements DatabaseAdapter {
    @Override public Capabilities capabilities() { return Capabilities.none(); }

    @Override public DatabaseSession connect(ConnectionProfile profile, char[] password) throws DatabaseException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(password, "password");
        if (!(profile instanceof SqliteConnectionSettings settings) || password.length != 0) {
            throw new DatabaseException(DatabaseException.Kind.POLICY);
        }
        if (!Files.isRegularFile(settings.file())) throw new DatabaseException(DatabaseException.Kind.NOT_FOUND);
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        config.enableLoadExtension(false);
        config.setBusyTimeout(settings.timeoutSeconds() * 1000);
        Connection connection = null;
        try {
            // URI encoding keeps literal '?', '#' and spaces in filenames out of driver options.
            connection = config.createConnection("jdbc:sqlite:" + settings.file().toUri().toASCIIString() + "?mode=ro");
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA query_only=ON"); }
            connection.setAutoCommit(false);
            var session = new SqliteSession(connection, settings.timeoutSeconds());
            session.listSchemas(); // Validate the file header before reporting a successful connection.
            return session;
        } catch (SQLException | DatabaseException failure) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) { /* Preserve the sanitized primary error. */ }
            }
            if (failure instanceof DatabaseException safe) throw safe;
            throw Failures.sanitize((SQLException) failure);
        }
    }
}
