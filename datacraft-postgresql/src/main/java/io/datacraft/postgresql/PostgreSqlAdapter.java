/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import org.postgresql.ds.PGSimpleDataSource;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;

/** PostgreSQL-specific connectivity; JDBC never appears in core contracts. */
public final class PostgreSqlAdapter implements DatabaseAdapter {
    private static final Capabilities CAPABILITIES = new Capabilities(Set.of(
            Capability.SCHEMAS, Capability.MATERIALIZED_VIEWS));

    @Override public Capabilities capabilities() { return CAPABILITIES; }

    @Override public DatabaseSession connect(ConnectionProfile profile, char[] password)
            throws DatabaseException {
        Objects.requireNonNull(profile, "profile");
        if (!(profile instanceof ConnectionSettings settings) || settings.kind() != DatabaseKind.POSTGRESQL) {
            throw new DatabaseException(DatabaseException.Kind.POLICY);
        }
        Objects.requireNonNull(password, "password");
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[] {settings.host()});
        source.setPortNumbers(new int[] {settings.port()});
        source.setDatabaseName(settings.database());
        source.setSslMode(settings.tlsMode() == TlsMode.VERIFY_FULL ? "verify-full" : "disable");
        source.setConnectTimeout(settings.timeoutSeconds());
        source.setSocketTimeout(settings.timeoutSeconds());
        source.setCancelSignalTimeout(5);
        source.setOptions("-c standard_conforming_strings=on");
        // The driver accepts only String passwords. Never set/retain it on the datasource.
        Connection connection = null;
        try {
            connection = source.getConnection(settings.username(), new String(password));
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            return new PostgreSqlSession(connection, settings.timeoutSeconds());
        } catch (SQLException failure) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) { /* Preserve sanitized primary failure. */ }
            }
            throw Failures.sanitize(failure);
        }
    }
}
