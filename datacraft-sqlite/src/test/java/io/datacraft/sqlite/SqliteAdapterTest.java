/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sqlite;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real native SQLite tests use only freshly migrated temporary files. No Docker needed. */
class SqliteAdapterTest {
    @TempDir Path directory;
    private Path file;
    private final SqliteAdapter adapter = new SqliteAdapter();
    private SqliteConnectionSettings settings;
    @BeforeEach void setup() throws Exception {
        file = directory.resolve("fixture # café.sqlite");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement();
                var fixture = getClass().getResourceAsStream("/migrations/001_metadata_and_query_fixture.sql")) {
            assertNotNull(fixture);
            String sql = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
            for (String migration : sql.split(";")) if (!migration.isBlank()) statement.execute(migration);
        }
        settings = new SqliteConnectionSettings(file, Environment.TEST, 2);
    }
    private DatabaseSession open() throws Exception { return adapter.connect(settings, new char[0]); }
    private static QueryResult query(DatabaseSession session, String sql) throws Exception {
        return session.query(new QueryRequest(sql, 100, 4096, 5), new QueryCancellation());
    }
    private static String value(QueryResult result) { return result.rows().getFirst().getFirst().value(); }
    private static final String EXPENSIVE = "WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000000000) SELECT sum(x) FROM n";

    @Test void discoversActualTablesViewsGeneratedAndUntypedColumns() throws Exception {
        try (var session = open()) {
            assertTrue(session.ping());
            assertEquals(List.of("main"), session.listSchemas());
            var relations = session.listRelations("main");
            assertEquals(4, relations.size());
            assertTrue(relations.contains(new RelationMetadata(new QualifiedName("main", "labels"), RelationKind.VIEW)));
            var columns = session.describeColumns(new QualifiedName("main", "Odd' Table"));
            assertEquals(List.of("id", "label", "computed"), columns.stream().map(ColumnMetadata::name).toList());
            assertFalse(columns.getFirst().nullable());
            assertTrue(columns.get(1).nullable());
            assertEquals("ANY", session.describeColumns(new QualifiedName("main", "untyped")).getFirst().databaseType());
            assertFalse(adapter.capabilities().supports(Capability.SCHEMAS));
            assertFalse(adapter.capabilities().supports(Capability.EDITABLE_RESULTS));
        }
    }
    @Test void missingObjectsAndInjectionNeverBroadenDiscovery() throws Exception {
        try (var session = open()) {
            assertTrue(session.listRelations("main' OR 1=1 --").isEmpty());
            assertEquals(DatabaseException.Kind.NOT_FOUND, assertThrows(DatabaseException.class,
                    () -> session.describeColumns(new QualifiedName("main", "x' OR 1=1 --"))).kind());
            assertEquals(DatabaseException.Kind.NOT_FOUND, assertThrows(DatabaseException.class,
                    () -> session.describeColumns(new QualifiedName("other", "dc"))).kind());
        }
    }
    @Test void distinguishesSqlitePrimaryKeyNullabilityRules() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement();
                var fixture = getClass().getResourceAsStream("/migrations/002_primary_key_fixture.sql")) {
            assertNotNull(fixture);
            for (String sql : new String(fixture.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
        try (var session = open()) {
            for (String name : List.of("integer_pk", "strict_pk", "composite_pk")) {
                assertTrue(session.describeColumns(new QualifiedName("main", name)).stream().noneMatch(ColumnMetadata::nullable), name);
            }
            for (String name : List.of("descending_pk", "text_pk")) {
                assertTrue(session.describeColumns(new QualifiedName("main", name)).getFirst().nullable(), name);
            }
        }
    }
    @Test void missingFileIsNeverCreatedAndNonDatabaseIsRejected() throws Exception {
        Path missing = directory.resolve("absent.sqlite");
        assertEquals(DatabaseException.Kind.NOT_FOUND, assertThrows(DatabaseException.class,
                () -> adapter.connect(new SqliteConnectionSettings(missing, Environment.TEST, 2), new char[0])).kind());
        assertFalse(Files.exists(missing));
        Path invalid = directory.resolve("invalid.sqlite"); Files.writeString(invalid, "This is not a database.");
        var failure = assertThrows(DatabaseException.class,
                () -> adapter.connect(new SqliteConnectionSettings(invalid, Environment.TEST, 2), new char[0]));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(invalid.toString()));
    }
    @Test void rejectsWrongProfileAndCredentialsWithoutModifyingCallerBuffer() {
        char[] password = {'x'};
        assertEquals(DatabaseException.Kind.POLICY,
                assertThrows(DatabaseException.class, () -> adapter.connect(settings, password)).kind());
        assertEquals('x', password[0]);
        assertThrows(DatabaseException.class, () -> adapter.connect(new ConnectionSettings("localhost", 5432,
                "test", "test", Environment.TEST, TlsMode.DISABLED, 2), new char[0]));
    }
    @Test void preservesNullEmptyLiteralNullAndDuplicateLabels() throws Exception {
        try (var session = open()) {
            var result = query(session, "SELECT label, label FROM \"Odd' Table\" ORDER BY id");
            assertEquals(List.of("label", "label"), result.columns().stream().map(QueryColumn::label).toList());
            assertEquals("alpha", value(result));
            assertTrue(result.rows().get(1).getFirst().isNull());
            assertEquals("", result.rows().get(2).getFirst().value());
            assertEquals("NULL", result.rows().get(3).getFirst().value());
        }
    }
    @Test void boundsRowsAndCellsIncludingUnicode() throws Exception {
        try (var session = open()) {
            var result = session.query(new QueryRequest("SELECT label FROM \"Odd' Table\" ORDER BY id", 1, 3, 2), new QueryCancellation());
            assertEquals(1, result.rows().size()); assertEquals("alp", value(result)); assertTrue(result.truncated());
            var unicode = session.query(new QueryRequest("SELECT 'a😀b'", 1, 2, 2), new QueryCancellation());
            assertEquals("a", value(unicode)); assertTrue(unicode.truncated());
            var large = session.query(new QueryRequest("SELECT printf('%1000000s', 'x')", 1, 16, 2), new QueryCancellation());
            assertEquals(16, value(large).length()); assertTrue(large.truncated());
        }
    }
    @Test void enforcesAggregateAndColumnLimits() throws Exception {
        try (var session = open()) {
            var result = session.query(new QueryRequest("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000) SELECT printf('%4096s','x') FROM n", 1000, 4096, 5), new QueryCancellation());
            assertEquals(488, result.rows().size()); assertTrue(result.truncated());
            String wide = "SELECT " + String.join(",", java.util.Collections.nCopies(129, "1"));
            assertEquals(DatabaseException.Kind.RESULT_LIMIT, assertThrows(DatabaseException.class, () -> query(session, wide)).kind());
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @Test void acceptsSqliteQuotingCommentsAndTrailingSemicolon() throws Exception {
        try (var session = open()) {
            assertEquals("semi;colon", value(query(session, "/* header */ SELECT 'semi;colon'; -- tail")));
            assertEquals("a'b", value(query(session, "SELECT 'a''b' AS `semi;colon`")));
            assertEquals("alpha", value(query(session, "SELECT [label] FROM \"Odd' Table\" WHERE id=1")));
            assertEquals("ordinary relation", value(query(session, "SELECT value FROM dc")));
        }
    }
    @Test void rejectsWritesScriptsControlsParametersAndExtensionLoadingWithoutChangingFile() throws Exception {
        byte[] before = Files.readAllBytes(file);
        try (var session = open()) {
            for (String sql : List.of("DELETE FROM dc", "CREATE TABLE bad(x)", "PRAGMA query_only=OFF", "ATTACH ':memory:' AS other", "BEGIN", "SELECT 1; DELETE FROM dc", "SELECT 1; SELECT 2", "SELECT ?", "SELECT :value")) {
                assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> query(session, sql), sql).kind());
            }
            assertThrows(DatabaseException.class, () -> query(session, "WITH n AS (SELECT 1) DELETE FROM dc RETURNING value"));
            assertThrows(DatabaseException.class, () -> query(session, "SELECT load_extension('not_allowed')"));
            assertEquals("4", value(query(session, "SELECT count(*) FROM \"Odd' Table\"")));
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }
    @Test void timeoutReleasesTransactionAndSessionCanBeReused() throws Exception {
        try (var session = open()) {
            assertEquals(DatabaseException.Kind.TIMEOUT, assertThrows(DatabaseException.class,
                    () -> session.query(new QueryRequest(EXPENSIVE, 1, 16, 1), new QueryCancellation())).kind());
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @Test void cancellationInterruptsCpuQueryAndSessionCanBeReused() throws Exception {
        try (var session = open(); var executor = Executors.newSingleThreadExecutor()) {
            var cancellation = new QueryCancellation();
            var started = new CountDownLatch(1);
            var future = executor.submit(() -> {
                started.countDown();
                return session.query(new QueryRequest(EXPENSIVE, 1, 16, 30), cancellation);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Thread.sleep(150); // Ensure the native VM is computing, rather than testing pre-cancellation only.
            assertFalse(future.isDone()); cancellation.cancel();
            var failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertEquals(DatabaseException.Kind.CANCELLED, ((DatabaseException) failure.getCause()).kind());
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @Test void errorsPreCancellationAndIdempotentCloseAreSafe() throws Exception {
        var session = open();
        var cancellation = new QueryCancellation(); cancellation.cancel();
        assertEquals(DatabaseException.Kind.CANCELLED, assertThrows(DatabaseException.class,
                () -> session.query(new QueryRequest("SELECT 1", 1, 1, 1), cancellation)).kind());
        assertThrows(DatabaseException.class, () -> query(session, "SELECT missing FROM absent"));
        assertEquals("42", value(query(session, "SELECT 42")));
        session.close(); session.close();
        assertEquals(DatabaseException.Kind.CLOSED, assertThrows(DatabaseException.class, session::ping).kind());
        Files.delete(file); assertFalse(Files.exists(file)); // No native handle left open on Windows.
    }
}
