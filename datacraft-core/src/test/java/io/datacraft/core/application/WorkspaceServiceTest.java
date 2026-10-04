/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.application;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceTest {
    private static final ConnectionSettings SETTINGS = new ConnectionSettings("localhost", 5432,
            "test", "test", Environment.TEST, TlsMode.DISABLED, 5);

    private static final class SessionDouble implements DatabaseSession {
        boolean closed;
        boolean failDiscovery;
        @Override public List<String> listSchemas() throws DatabaseException {
            if (failDiscovery) throw new DatabaseException(DatabaseException.Kind.PERMISSION);
            return List.of("public");
        }
        @Override public boolean ping() { return !closed; }
        @Override public List<RelationMetadata> listRelations(String schema) { return List.of(); }
        @Override public List<ColumnMetadata> describeColumns(QualifiedName name) { return List.of(); }
        @Override public QueryResult query(QueryRequest request, QueryCancellation cancellation) {
            return new QueryResult(List.of(new QueryColumn("value", "integer")),
                    List.of(List.of(new QueryCell("1", false))), false, 1);
        }
        @Override public void close() { closed = true; }
    }
    private DatabaseAdapter adapter(SessionDouble session) {
        return new DatabaseAdapter() {
            @Override public Capabilities capabilities() { return Capabilities.none(); }
            @Override public DatabaseSession connect(ConnectionProfile settings, char[] password) { return session; }
        };
    }
    @Test void connectConsumesCredentialBufferAndCloseReleasesSession() throws Exception {
        var session = new SessionDouble();
        var workspace = new WorkspaceService(new AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, adapter(session))));
        char[] buffer = {'x'};
        assertEquals(List.of("public"), workspace.connect(SETTINGS, buffer));
        assertEquals('\0', buffer[0]);
        assertTrue(workspace.isConnected());
        workspace.close();
        workspace.close();
        assertTrue(session.closed);
        assertFalse(workspace.isConnected());
        assertEquals(DatabaseException.Kind.CLOSED,
                assertThrows(DatabaseException.class, workspace::schemas).kind());
    }
    @Test void partialConnectFailureWipesBufferAndClosesCandidate() {
        var session = new SessionDouble();
        session.failDiscovery = true;
        var workspace = new WorkspaceService(new AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, adapter(session))));
        char[] buffer = {'x'};
        assertThrows(DatabaseException.class, () -> workspace.connect(SETTINGS, buffer));
        assertTrue(session.closed);
        assertFalse(workspace.isConnected());
        assertEquals('\0', buffer[0]);
    }
    @Test void secondConnectCannotLeakFirstSessionAndStillWipesNewBuffer() throws Exception {
        var session = new SessionDouble();
        try (var workspace = new WorkspaceService(new AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, adapter(session))))) {
            workspace.connect(SETTINGS, new char[0]);
            char[] buffer = {'x'};
            assertThrows(DatabaseException.class, () -> workspace.connect(SETTINGS, buffer));
            assertEquals('\0', buffer[0]);
            assertFalse(session.closed);
        }
    }
}
