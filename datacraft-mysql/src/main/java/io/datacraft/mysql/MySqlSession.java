/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import java.sql.*;
import java.util.*;
import io.datacraft.core.adapter.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;

final class MySqlSession implements DatabaseSession {
    private final Connection connection;
    private final int timeout;
    private boolean closed;
    MySqlSession(Connection connection, int timeout) { this.connection = connection; this.timeout = timeout; }
    private void ensureOpen() throws DatabaseException { if (closed) throw new DatabaseException(DatabaseException.Kind.CLOSED); }
    @Override public boolean ping() throws DatabaseException {
        ensureOpen();
        try { return connection.isValid(timeout); }
        catch (SQLException failure) { throw Failures.sanitize(failure); }
    }
    @Override public List<String> listSchemas() throws DatabaseException {
        ensureOpen(); var result = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                SELECT SCHEMA_NAME FROM information_schema.SCHEMATA
                WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys') ORDER BY SCHEMA_NAME
                """)) {
            statement.setQueryTimeout(timeout);
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
            return List.copyOf(result);
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }
    @Override public List<RelationMetadata> listRelations(String schema) throws DatabaseException {
        Objects.requireNonNull(schema); ensureOpen(); var result = new ArrayList<RelationMetadata>();
        try (var statement = connection.prepareStatement("""
                SELECT TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=? AND TABLE_TYPE IN ('BASE TABLE','VIEW') ORDER BY TABLE_NAME
                """)) {
            statement.setQueryTimeout(timeout); statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(new RelationMetadata(new QualifiedName(schema, rows.getString(1)),
                        rows.getString(2).equals("VIEW") ? RelationKind.VIEW : RelationKind.TABLE));
            }
            return List.copyOf(result);
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }
    @Override public List<ColumnMetadata> describeColumns(QualifiedName relation) throws DatabaseException {
        Objects.requireNonNull(relation); ensureOpen(); var result = new ArrayList<ColumnMetadata>();
        try (var statement = connection.prepareStatement("""
                SELECT COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION
                """)) {
            statement.setQueryTimeout(timeout); statement.setString(1, relation.schema()); statement.setString(2, relation.name());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(new ColumnMetadata(rows.getString(1), rows.getInt(2), rows.getString(3), rows.getString(4).equals("YES")));
            }
            if (result.isEmpty()) throw new DatabaseException(DatabaseException.Kind.NOT_FOUND);
            return List.copyOf(result);
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }

    @SuppressWarnings("try") // Cancellation registrations deterministically detach driver callbacks.
    @Override public QueryResult query(QueryRequest request, QueryCancellation cancellation) throws DatabaseException {
        Objects.requireNonNull(request); Objects.requireNonNull(cancellation); ensureOpen();
        if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
        String sql = MySqlQueryPolicy.singleSelect(request.sql());
        long start = System.nanoTime();
        try {
            connection.setNetworkTimeout(Runnable::run, (request.timeoutSeconds() + 5) * 1000);
            var columns = new ArrayList<QueryColumn>();
            // Server-side preparation describes original labels without evaluating rows/functions.
            try (var description = connection.prepareStatement(sql);
                    var registration = cancellation.onCancel(() -> cancel(description))) {
                description.setQueryTimeout(request.timeoutSeconds());
                var metadata = description.getMetaData();
                if (metadata == null || metadata.getColumnCount() == 0) throw new DatabaseException(DatabaseException.Kind.POLICY);
                if (metadata.getColumnCount() > QueryResultBuilder.MAX_COLUMNS) throw new DatabaseException(DatabaseException.Kind.RESULT_LIMIT);
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(new QueryColumn(metadata.getColumnLabel(index), metadata.getColumnTypeName(index)));
                }
            }
            var aliases = new ArrayList<String>(); var projection = new ArrayList<String>();
            for (int index = 0; index < columns.size(); index++) {
                aliases.add("c" + index);
                projection.add("LEFT(CAST(dc.c" + index + " AS CHAR CHARACTER SET utf8mb4)," + (request.cellCharacterLimit() + 1) + ")");
            }
            String internalName = "dc_" + UUID.randomUUID().toString().replace("-", "");
            String bounded = "WITH " + internalName + "(" + String.join(",", aliases) + ") AS (\n" + sql + "\n) SELECT "
                    + String.join(",", projection) + " FROM " + internalName + " AS dc LIMIT " + (request.rowLimit() + 1);
            long remaining = request.timeoutSeconds() * 1000L - (System.nanoTime() - start) / 1_000_000;
            if (remaining <= 0) throw new DatabaseException(DatabaseException.Kind.TIMEOUT);
            if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
            var result = new QueryResultBuilder(request, columns);
            try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    var registration = cancellation.onCancel(() -> cancel(statement))) {
                statement.setQueryTimeout((int) Math.max(1, (remaining + 999) / 1000));
                statement.setFetchSize(50); statement.setMaxRows(request.rowLimit() + 1);
                if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                try (var rows = statement.executeQuery(bounded)) {
                    while (rows.next()) {
                        if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                        var values = new ArrayList<String>();
                        for (int index = 1; index <= columns.size(); index++) values.add(rows.getString(index));
                        if (!result.addRow(values)) break;
                    }
                }
            }
            if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
            return result.build((System.nanoTime() - start) / 1_000_000);
        } catch (SQLException failure) {
            if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
            throw Failures.sanitize(failure);
        } finally {
            try { connection.setNetworkTimeout(Runnable::run, timeout * 1000); }
            catch (SQLException failure) { invalidate(); throw Failures.sanitize(failure); }
            finally { if (!closed) release(); }
        }
    }
    private static void cancel(Statement statement) {
        try { statement.cancel(); } catch (SQLException ignored) { /* Network and statement timeouts remain finite fallbacks. */ }
    }
    private void release() throws DatabaseException {
        try { connection.rollback(); }
        catch (SQLException failure) { invalidate(); throw Failures.sanitize(failure); }
    }
    private void invalidate() {
        closed = true;
        try { connection.close(); } catch (SQLException ignored) { /* Never retain driver causes. */ }
    }
    @Override public void close() throws DatabaseException {
        if (closed) return;
        try { connection.close(); closed = true; }
        catch (SQLException failure) { throw Failures.sanitize(failure); }
    }
}
