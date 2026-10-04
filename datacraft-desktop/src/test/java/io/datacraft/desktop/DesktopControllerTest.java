/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.*;
import io.datacraft.core.application.WorkspaceService;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopControllerTest {
    @Test void runsServicesOffCallerThreadAndCancellationOffQueryWorker() throws Exception {
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var closed = new AtomicBoolean();
        var session = new DatabaseSession() {
            @Override public boolean ping() { return true; }
            @Override public List<String> listSchemas() {
                assertEquals("datacraft-workspace", Thread.currentThread().getName());
                return List.of("public");
            }
            @Override public List<RelationMetadata> listRelations(String schema) { return List.of(); }
            @Override public List<ColumnMetadata> describeColumns(QualifiedName relation) { return List.of(); }
            @Override @SuppressWarnings("try") public QueryResult query(QueryRequest request, QueryCancellation signal)
                    throws DatabaseException {
                try (var registration = signal.onCancel(() -> {
                    assertEquals("datacraft-cancel", Thread.currentThread().getName());
                    stopped.countDown();
                })) {
                    started.countDown();
                    try { assertTrue(stopped.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    throw new DatabaseException(DatabaseException.Kind.CANCELLED);
                }
            }
            @Override public void close() { closed.set(true); }
        };
        var adapter = new DatabaseAdapter() {
            @Override public Capabilities capabilities() { return Capabilities.none(); }
            @Override public DatabaseSession connect(ConnectionProfile settings, char[] password) { return session; }
        };
        var controller = new DesktopController(new WorkspaceService(new io.datacraft.core.adapter.AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, adapter))));
        try {
            char[] buffer = {'x'};
            controller.connect(new ConnectionSettings("localhost", 5432, "test", "test",
                    Environment.TEST, TlsMode.DISABLED, 5), buffer).get(5, TimeUnit.SECONDS);
            assertEquals('\0', buffer[0]);
            var pending = controller.query(new QueryRequest("SELECT 1", 1, 1, 1));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(controller.analyze("SELECT 1", DatabaseKind.POSTGRESQL).get(2, TimeUnit.SECONDS).diagnostics().isEmpty());
            assertEquals(List.of("public"), controller.metadata().schemas());
            controller.cancel();
            assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
        } finally { controller.shutdown().get(5, TimeUnit.SECONDS); }
        assertTrue(closed.get());
        assertEquals(io.datacraft.sql.SchemaSnapshot.EMPTY, controller.metadata());
        assertThrows(RejectedExecutionException.class, controller::schemas);
    }
}
