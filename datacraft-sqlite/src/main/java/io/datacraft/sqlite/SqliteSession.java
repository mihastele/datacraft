/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sqlite;

import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteConnection;
import io.datacraft.core.adapter.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;

final class SqliteSession implements DatabaseSession {
    private final Connection connection;
    private final int timeout;
    private boolean closed;
    SqliteSession(Connection connection, int timeout) { this.connection = connection; this.timeout = timeout; }

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
        beginMetadata();
        // Access the catalog to reject a regular file that is not a SQLite database.
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM main.sqlite_schema")) {
            rows.next();
            return List.of("main"); // Namespace for QualifiedName, not a claim of server-schema support.
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }
    @Override public List<RelationMetadata> listRelations(String schema) throws DatabaseException {
        Objects.requireNonNull(schema);
        ensureOpen();
        if (!schema.equals("main")) return List.of();
        beginMetadata();
        var result = new ArrayList<RelationMetadata>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT name, type FROM main.sqlite_schema
                WHERE type IN ('table', 'view') AND name NOT GLOB 'sqlite_*' ORDER BY name
                """)) {
            while (rows.next()) result.add(new RelationMetadata(new QualifiedName("main", rows.getString(1)),
                    rows.getString(2).equals("view") ? RelationKind.VIEW : RelationKind.TABLE));
            return List.copyOf(result);
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }
    @Override public List<ColumnMetadata> describeColumns(QualifiedName relation) throws DatabaseException {
        Objects.requireNonNull(relation);
        ensureOpen();
        if (!relation.schema().equals("main")) throw new DatabaseException(DatabaseException.Kind.NOT_FOUND);
        beginMetadata();
        var result = new ArrayList<ColumnMetadata>();
        try (var exists = connection.prepareStatement("SELECT 1 FROM main.sqlite_schema WHERE name=? AND type IN ('table','view')");
                var statement = connection.prepareStatement("""
                        SELECT cid, name, type,
                          "notnull" OR (pk > 0 AND (
                            (SELECT wr OR strict FROM pragma_table_list WHERE schema='main' AND name=?)
                            OR (upper(type)='INTEGER'
                              AND (SELECT count(*) FROM pragma_table_xinfo(?, 'main') WHERE pk>0)=1
                              AND NOT EXISTS (SELECT 1 FROM pragma_index_list(?, 'main') WHERE origin='pk'))
                          )), hidden
                        FROM pragma_table_xinfo(?, 'main') ORDER BY cid
                        """)) {
            exists.setString(1, relation.name());
            try (var rows = exists.executeQuery()) {
                if (!rows.next()) throw new DatabaseException(DatabaseException.Kind.NOT_FOUND);
            }
            for (int index = 1; index <= 4; index++) statement.setString(index, relation.name());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getInt(5) == 1) continue; // Hidden virtual-table inputs are not ordinary result columns.
                    String type = rows.getString(3);
                    result.add(new ColumnMetadata(rows.getString(2), rows.getInt(1) + 1,
                            type == null || type.isEmpty() ? "ANY" : type, !rows.getBoolean(4)));
                }
            }
            return List.copyOf(result);
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
        finally { release(); }
    }

    @SuppressWarnings("try") // Registration detaches the native interrupt callback after execution.
    @Override public QueryResult query(QueryRequest request, QueryCancellation cancellation) throws DatabaseException {
        Objects.requireNonNull(request); Objects.requireNonNull(cancellation);
        ensureOpen();
        if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
        String sql = SqliteQueryPolicy.singleSelect(request.sql());
        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(request.timeoutSeconds());
        try (var registration = cancellation.onCancel(() -> {
            try { ((SQLiteConnection) connection).getDatabase().interrupt(); }
            catch (SQLException ignored) { /* Progress callback and finite busy wait remain fallbacks. */ }
        })) {
            ((SQLiteConnection) connection).setBusyTimeout(Math.min(request.timeoutSeconds(), 1) * 1000);
            ProgressHandler.setHandler(connection, 1000, new ProgressHandler() {
                @Override protected int progress() { return cancellation.isCancelled() || System.nanoTime() >= deadline ? 1 : 0; }
            });
            var columns = new ArrayList<QueryColumn>();
            try (var description = connection.prepareStatement(sql)) {
                var metadata = description.getMetaData();
                if (metadata.getColumnCount() > QueryResultBuilder.MAX_COLUMNS) throw new DatabaseException(DatabaseException.Kind.RESULT_LIMIT);
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    columns.add(new QueryColumn(metadata.getColumnLabel(i), metadata.getColumnTypeName(i)));
                }
            }
            var result = new QueryResultBuilder(request, columns);
            var aliases = new ArrayList<String>();
            var projection = new ArrayList<String>();
            for (int i = 0; i < columns.size(); i++) {
                aliases.add("c" + i);
                projection.add("substr(CAST(dc.c" + i + " AS TEXT),1," + (request.cellCharacterLimit() + 1) + ")");
            }
            // SQLite CTE column aliases preserve ordinal values even when original labels repeat.
            String resultName = "dc_" + java.util.UUID.randomUUID().toString().replace("-", "");
            String bounded = "WITH " + resultName + "(" + String.join(",", aliases) + ") AS (\n" + sql + "\n) SELECT "
                    + String.join(",", projection) + " FROM " + resultName + " AS dc LIMIT " + (request.rowLimit() + 1);
            checkDeadline(cancellation, deadline);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(bounded)) {
                while (rows.next()) {
                    checkDeadline(cancellation, deadline);
                    var values = new ArrayList<String>();
                    for (int i = 1; i <= columns.size(); i++) values.add(rows.getString(i));
                    if (!result.addRow(values)) break;
                }
            }
            checkDeadline(cancellation, deadline);
            return result.build((System.nanoTime() - start) / 1_000_000);
        } catch (SQLException failure) {
            checkDeadline(cancellation, deadline);
            throw Failures.sanitize(failure);
        } finally {
            try {
                ProgressHandler.clearHandler(connection);
                ((SQLiteConnection) connection).setBusyTimeout(timeout * 1000);
            } catch (SQLException failure) { invalidate(); throw Failures.sanitize(failure); }
            finally { release(); }
        }
    }
    private static void checkDeadline(QueryCancellation cancellation, long deadline) throws DatabaseException {
        if (cancellation.isCancelled()) throw new DatabaseException(DatabaseException.Kind.CANCELLED);
        if (System.nanoTime() >= deadline) throw new DatabaseException(DatabaseException.Kind.TIMEOUT);
    }
    private void beginMetadata() throws DatabaseException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        try {
            ProgressHandler.setHandler(connection, 1000, new ProgressHandler() {
                @Override protected int progress() { return System.nanoTime() >= deadline ? 1 : 0; }
            });
        } catch (SQLException failure) { throw Failures.sanitize(failure); }
    }
    private void release() throws DatabaseException {
        if (closed) return;
        try { ProgressHandler.clearHandler(connection); connection.rollback(); }
        catch (SQLException failure) { invalidate(); throw Failures.sanitize(failure); }
    }
    private void invalidate() {
        closed = true;
        try { connection.close(); } catch (SQLException ignored) { /* Safe diagnostics only. */ }
    }
    @Override public void close() throws DatabaseException {
        if (closed) return;
        try { connection.close(); closed = true; }
        catch (SQLException failure) { throw Failures.sanitize(failure); }
    }
}
