/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import org.mariadb.jdbc.MariaDbDataSource;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;

/** Shared protocol adapter; driver and dialect details never escape into the core. */
public final class MySqlMariaDbAdapter implements DatabaseAdapter {
    @Override public Capabilities capabilities() { return new Capabilities(Set.of(Capability.MULTIPLE_DATABASES)); }

    @Override public DatabaseSession connect(ConnectionProfile profile, char[] password) throws DatabaseException {
        Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(password, "password");
        if (!(profile instanceof ConnectionSettings settings)
                || (settings.kind() != DatabaseKind.MYSQL && settings.kind() != DatabaseKind.MARIADB)) {
            throw new DatabaseException(DatabaseException.Kind.POLICY);
        }
        Connection connection = null;
        try {
            var source = new MariaDbDataSource(connectionUrl(settings));
            connection = source.getConnection(settings.username(), new String(password));
            connection.setCatalog(settings.database()); // Structured selection, never an interpolated URL path.
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(settings.timeoutSeconds());
                // A JDBC hint alone is insufficient. Enforce the default on the server too.
                statement.execute("SET SESSION TRANSACTION READ ONLY");
                statement.execute("SET SESSION sql_mode=CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES')");
                statement.execute("SET NAMES utf8mb4");
            }
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            return new MySqlSession(connection, settings.timeoutSeconds());
        } catch (SQLException failure) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) { /* Preserve safe primary error. */ }
            }
            throw Failures.sanitize(failure);
        }
    }

    static String connectionUrl(ConnectionSettings settings) {
        String host = settings.host();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
        return "jdbc:mariadb://" + host + ":" + settings.port() + "/"
                + "?sslMode=" + (settings.tlsMode() == TlsMode.VERIFY_FULL ? "verify-full" : "disable")
                + "&connectTimeout=" + settings.timeoutSeconds() * 1000 + "&socketTimeout=" + settings.timeoutSeconds() * 1000
                + "&allowMultiQueries=false&allowLocalInfile=false&allowPublicKeyRetrieval=false&useServerPrepStmts=true";
    }
}
