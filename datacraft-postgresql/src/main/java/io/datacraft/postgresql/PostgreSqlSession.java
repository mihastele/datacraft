/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.postgresql;

import java.sql.*;
import java.util.*;
import io.datacraft.core.adapter.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;
import org.postgresql.core.Parser;
import org.postgresql.core.SqlCommandType;

final class PostgreSqlSession implements DatabaseSession {
    private final Connection connection;
    private final int timeout;
    private boolean closed;

    PostgreSqlSession(Connection connection, int timeout) {
        this.connection = connection;
        this.timeout = timeout;
    }

    private void ensureOpen() throws DatabaseException {
        if (closed) throw new DatabaseException(DatabaseException.Kind.CLOSED);
    }

    @Override public boolean ping() throws DatabaseException {
        ensureOpen();
        try { return connection.isValid(timeout); }
        catch (SQLException failure) { throw Failures.sanitize(failure); }
    }

    @Override public List<String> listSchemas() throws DatabaseException {
        ensureOpen();
        var result = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT nspname FROM pg_catalog.pg_namespace
                WHERE nspname <> 'information_schema' AND nspname !~ '^pg_'
                  AND pg_catalog.has_schema_privilege(oid, 'USAGE')
                ORDER BY nspname
                """)) {
            statement.setQueryTimeout(timeout);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
            connection.rollback();
            return List.copyOf(result);
        } catch (SQLException failure) { throw recover(failure); }
    }

    @Override public List<RelationMetadata> listRelations(String schema) throws DatabaseException {
        Objects.requireNonNull(schema, "schema");
        ensureOpen();
        var result = new ArrayList<RelationMetadata>();
        try (var statement = connection.prepareStatement("""
                SELECT c.relname, c.relkind FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r','p','v','m','f')
                  AND pg_catalog.has_schema_privilege(n.oid, 'USAGE')
                ORDER BY c.relname
                """)) {
            statement.setQueryTimeout(timeout);
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var kind = switch (rows.getString(2)) {
                        case "v" -> RelationKind.VIEW;
                        case "m" -> RelationKind.MATERIALIZED_VIEW;
                        case "f" -> RelationKind.FOREIGN_TABLE;
                        default -> RelationKind.TABLE;
                    };
                    result.add(new RelationMetadata(new QualifiedName(schema, rows.getString(1)), kind));
                }
            }
            connection.rollback();
            return List.copyOf(result);
        } catch (SQLException failure) { throw recover(failure); }
    }

    @Override public List<ColumnMetadata> describeColumns(QualifiedName relation) throws DatabaseException {
        Objects.requireNonNull(relation, "relation");
        ensureOpen();
        var result = new ArrayList<ColumnMetadata>();
        boolean found = false;
        try (var statement = connection.prepareStatement("""
                SELECT c.oid, a.attname, a.attnum,
                       pg_catalog.format_type(a.atttypid, a.atttypmod),
                       NOT (a.attnotnull OR COALESCE(t.typnotnull, false))
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_catalog.pg_attribute a
                  ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
                WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('r','p','v','m','f')
                  AND pg_catalog.has_schema_privilege(n.oid, 'USAGE')
                ORDER BY a.attnum
                """)) {
            statement.setQueryTimeout(timeout);
            statement.setString(1, relation.schema());
            statement.setString(2, relation.name());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    found = true;
                    if (rows.getString(2) != null) {
                        result.add(new ColumnMetadata(rows.getString(2), rows.getInt(3),
                                rows.getString(4), rows.getBoolean(5)));
                    }
                }
            }
            connection.rollback();
        } catch (SQLException failure) { throw recover(failure); }
        if (!found) throw new DatabaseException(DatabaseException.Kind.NOT_FOUND);
        return List.copyOf(result);
    }

    private DatabaseException recover(SQLException failure) {
        try { connection.rollback(); } catch (SQLException ignored) { /* Do not expose driver diagnostics. */ }
        return Failures.sanitize(failure);
    }

    @SuppressWarnings("try") // Registration exists for deterministic callback detachment.
    @Override public QueryResult query(QueryRequest request, QueryCancellation cancellation)
            throws DatabaseException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        ensureOpen();
        if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
        long start = System.nanoTime();
        try {
            var parsed = Parser.parseJdbcSql(request.sql(), true, false, true, false, true).stream()
                    .filter(query -> !onlyTrivia(query.nativeSql)).toList();
            ReadOnlyQueryPolicy.requireSingleSelect(parsed.size(), parsed.size() == 1
                    && parsed.getFirst().command.getType() == SqlCommandType.SELECT);
            String sql = parsed.getFirst().nativeSql;
            connection.setNetworkTimeout(Runnable::run, (request.timeoutSeconds() + 5) * 1000);
            try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setQueryTimeout(request.timeoutSeconds());
                statement.setFetchSize(Math.min(50, request.rowLimit() + 1));
                statement.setMaxRows(request.rowLimit() + 1);
                try (var registration = cancellation.onCancel(() -> {
                    try { statement.cancel(); } catch (SQLException ignored) { /* Timeout remains the fallback. */ }
                })) {
                    if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                    var columns = new ArrayList<QueryColumn>();
                    // Describe without fetching data. The outer projection then bounds values on the server.
                    try (var description = statement.executeQuery("SELECT * FROM (\n" + sql + "\n) AS dc LIMIT 0")) {
                        var metadata = description.getMetaData();
                        if (metadata.getColumnCount() > 128) {
                            throw new DatabaseException(DatabaseException.Kind.RESULT_LIMIT);
                        }
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            columns.add(new QueryColumn(metadata.getColumnLabel(i), metadata.getColumnTypeName(i)));
                        }
                    }
                    var projection = new ArrayList<String>();
                    var aliases = new ArrayList<String>();
                    for (int i = 0; i < columns.size(); i++) {
                        String alias = "c" + i;
                        aliases.add(alias);
                        projection.add("pg_catalog.left(dc." + alias + "::pg_catalog.text, "
                                + (request.cellCharacterLimit() + 1) + ")");
                    }
                    if (columns.isEmpty()) throw new DatabaseException(DatabaseException.Kind.RESULT_LIMIT);
                    String boundedSql = "SELECT " + String.join(",", projection) + " FROM (\n" + sql
                            + "\n) AS dc(" + String.join(",", aliases) + ") LIMIT " + (request.rowLimit() + 1);
                    if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                    long remainingMillis = request.timeoutSeconds() * 1000L - (System.nanoTime() - start) / 1_000_000;
                    if (remainingMillis <= 0) throw new DatabaseException(DatabaseException.Kind.TIMEOUT);
                    statement.setQueryTimeout((int) Math.max(1, (remainingMillis + 999) / 1000));
                    var result = new QueryResultBuilder(request, columns);
                    try (var rows = statement.executeQuery(boundedSql)) {
                        while (rows.next()) {
                            if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                            var values = new ArrayList<String>();
                            for (int i = 1; i <= columns.size(); i++) values.add(rows.getString(i));
                            if (!result.addRow(values)) break;
                        }
                    }
                    if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                    return result.build((System.nanoTime() - start) / 1_000_000);
                }
            }
        } catch (SQLException failure) {
            if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
            throw Failures.sanitize(failure);
        } finally {
            try {
                connection.rollback();
                connection.setNetworkTimeout(Runnable::run, timeout * 1000);
            } catch (SQLException failure) {
                // A failed rollback cannot leave a usable session in unknown transaction state.
                try { connection.close(); } catch (SQLException ignored) { /* Sanitized cleanup only. */ }
                closed = true;
                throw Failures.sanitize(failure);
            }
        }
    }

    /** pgJDBC includes trailing comment-only fragments in its split list. */
    private static boolean onlyTrivia(String sql) {
        int index = 0;
        while (index < sql.length()) {
            if (Character.isWhitespace(sql.charAt(index))) { index++; continue; }
            if (sql.startsWith("--", index)) {
                index += 2;
                while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') index++;
                continue;
            }
            if (sql.startsWith("/*", index)) {
                int depth = 1;
                index += 2;
                while (index < sql.length() && depth > 0) {
                    if (sql.startsWith("/*", index)) { depth++; index += 2; }
                    else if (sql.startsWith("*/", index)) { depth--; index += 2; }
                    else index++;
                }
                if (depth != 0) return false;
                continue;
            }
            return false;
        }
        return true;
    }

    @Override public void close() throws DatabaseException {
        if (closed) return;
        try { connection.close(); closed = true; }
        catch (SQLException failure) { throw Failures.sanitize(failure); }
    }
}
