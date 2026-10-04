/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.application;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.ConnectionProfile;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;

/** Single-owner application workflow; clients schedule blocking calls on their worker. */
public final class WorkspaceService implements AutoCloseable {
    private final AdapterRegistry adapters;
    private DatabaseSession session;

    public WorkspaceService(AdapterRegistry adapters) { this.adapters = Objects.requireNonNull(adapters); }

    /** Consumes and wipes the supplied password array on every exit path. */
    public List<String> connect(ConnectionProfile settings, char[] password) throws DatabaseException {
        Objects.requireNonNull(password, "password");
        DatabaseSession candidate = null;
        try {
            if (session != null) throw new DatabaseException(DatabaseException.Kind.POLICY);
            candidate = adapters.resolve(settings.kind()).connect(settings, password);
            var schemas = candidate.listSchemas();
            session = candidate;
            return schemas;
        } catch (DatabaseException | RuntimeException failure) {
            if (candidate != null) {
                try { candidate.close(); } catch (DatabaseException ignored) { /* Preserve primary safe failure. */ }
            }
            throw failure;
        } finally { Arrays.fill(password, '\0'); }
    }

    private DatabaseSession session() throws DatabaseException {
        if (session == null) throw new DatabaseException(DatabaseException.Kind.CLOSED);
        return session;
    }
    public List<String> schemas() throws DatabaseException { return session().listSchemas(); }
    public List<RelationMetadata> relations(String schema) throws DatabaseException { return session().listRelations(schema); }
    public List<ColumnMetadata> columns(QualifiedName name) throws DatabaseException { return session().describeColumns(name); }
    public QueryResult query(QueryRequest request, QueryCancellation signal) throws DatabaseException {
        return session().query(request, signal);
    }
    public java.util.Set<io.datacraft.core.connection.DatabaseKind> availableDatabases() { return adapters.available(); }
    public boolean isConnected() { return session != null; }
    @Override public void close() throws DatabaseException {
        if (session != null) { session.close(); session = null; }
    }
}
