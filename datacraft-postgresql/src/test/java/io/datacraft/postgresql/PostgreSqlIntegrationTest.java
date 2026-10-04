/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.postgresql;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import io.datacraft.core.query.*;
import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class PostgreSqlIntegrationTest {
    // Immutable image selection; credentials exist only at runtime in this disposable resource.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f")
            .withDatabaseName("datacraft_test")
            .withUsername("datacraft_test")
            .withPassword(UUID.randomUUID().toString());
    private final DatabaseAdapter adapter = new PostgreSqlAdapter();

    @BeforeAll static void provision() throws Exception {
        try {
            POSTGRES.start(); // Docker/setup failures fail the suite; never silently skip.
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement()) {
                for (String migration : new String[] {"001_metadata_fixture.sql", "002_query_fixture.sql"}) {
                    try (var resource = PostgreSqlIntegrationTest.class.getResourceAsStream("/migrations/" + migration)) {
                        assertNotNull(resource);
                        statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception failure) {
            POSTGRES.stop();
            throw failure;
        }
    }
    @AfterAll static void cleanup() { POSTGRES.stop(); }

    private ConnectionSettings settings(TlsMode tls) {
        return new ConnectionSettings(POSTGRES.getHost(), POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(), POSTGRES.getUsername(), Environment.TEST, tls, 5);
    }
    private DatabaseSession connect() throws DatabaseException {
        char[] password = POSTGRES.getPassword().toCharArray();
        try { return adapter.connect(settings(TlsMode.DISABLED), password); }
        finally { Arrays.fill(password, '\0'); }
    }

    @Test void discoversRealMetadataAndPreservesIdentifiersAndTypes() throws Exception {
        try (var session = connect()) {
            assertTrue(session.ping());
            var schemas = session.listSchemas();
            assertTrue(schemas.contains("craft_%"));
            assertFalse(schemas.contains("pg_catalog"));
            assertThrows(UnsupportedOperationException.class, () -> schemas.add("other"));
            Map<String, RelationKind> relations = session.listRelations("craft_%").stream()
                    .collect(Collectors.toMap(r -> r.name().name(), RelationMetadata::kind));
            assertEquals(RelationKind.TABLE, relations.get("Odd' Table"));
            assertEquals(RelationKind.VIEW, relations.get("sample_view"));
            assertEquals(RelationKind.MATERIALIZED_VIEW, relations.get("sample_materialized"));
            assertEquals(RelationKind.TABLE, relations.get("partitioned"));
            var columns = session.describeColumns(new QualifiedName("craft_%", "Odd' Table"));
            assertEquals(6, columns.size());
            assertEquals(new ColumnMetadata("id", 1, "integer", false), columns.getFirst());
            assertEquals("Camel Column", columns.get(1).name());
            assertEquals(3, columns.get(1).ordinal()); // Dropped columns do not renumber catalog ordinals.
            assertTrue(columns.get(1).nullable());
            assertEquals("jsonb", columns.get(2).databaseType());
            assertEquals("text[]", columns.get(3).databaseType());
            assertFalse(columns.get(4).nullable()); // NOT NULL inherited from domain.
            assertTrue(columns.get(5).databaseType().contains("mood"));
            assertThrows(UnsupportedOperationException.class, columns::clear);
        }
    }

    @Test void missingAndZeroColumnObjectsAreDistinctAndNamesCannotInjectSql() throws Exception {
        try (var session = connect()) {
            assertTrue(session.describeColumns(new QualifiedName("craft_%", "empty_table")).isEmpty());
            var failure = assertThrows(DatabaseException.class,
                    () -> session.describeColumns(new QualifiedName("craft_%", "' OR true --")));
            assertEquals(DatabaseException.Kind.NOT_FOUND, failure.kind());
            assertTrue(session.listRelations("craft_%' OR true --").isEmpty());
            assertTrue(session.ping()); // A missing object does not poison the session.
        }
    }

    @Test void closesIdempotentlyAndRejectsFurtherOperations() throws Exception {
        var session = connect();
        session.close();
        session.close();
        assertEquals(DatabaseException.Kind.CLOSED,
                assertThrows(DatabaseException.class, session::ping).kind());
        assertEquals(DatabaseException.Kind.CLOSED,
                assertThrows(DatabaseException.class, session::listSchemas).kind());
        assertEquals(DatabaseException.Kind.CLOSED,
                assertThrows(DatabaseException.class, () -> session.listRelations("public")).kind());
        assertEquals(DatabaseException.Kind.CLOSED,
                assertThrows(DatabaseException.class,
                        () -> session.describeColumns(new QualifiedName("public", "missing"))).kind());
    }

    @Test void authenticationFailuresAreSanitizedAndCallerPasswordIsNotModified() {
        char[] incorrect = UUID.randomUUID().toString().toCharArray();
        char[] original = incorrect.clone();
        try {
            var failure = assertThrows(DatabaseException.class,
                    () -> adapter.connect(settings(TlsMode.DISABLED), incorrect));
            assertEquals(DatabaseException.Kind.AUTHENTICATION, failure.kind());
            assertNull(failure.getCause());
            assertTrue(Arrays.equals(original, incorrect)); // Do not print sensitive arrays on failure.
        } finally {
            Arrays.fill(incorrect, '\0');
            Arrays.fill(original, '\0');
        }
    }

    @Test void verifiedTlsDoesNotDowngradeToPlaintext() {
        char[] password = POSTGRES.getPassword().toCharArray();
        try {
            var failure = assertThrows(DatabaseException.class,
                    () -> adapter.connect(settings(TlsMode.VERIFY_FULL), password));
            assertEquals(DatabaseException.Kind.CONNECTION, failure.kind());
        } finally { Arrays.fill(password, '\0'); }
    }

    @Test void doesNotLeaveIdleTransactionsOrLeakSessionConnections() throws Exception {
        try (var session = connect()) {
            session.listSchemas();
            session.listRelations("craft_%");
            session.describeColumns(new QualifiedName("craft_%", "Odd' Table"));
            try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = admin.createStatement();
                    var rows = statement.executeQuery("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND state = 'idle in transaction'
                            """)) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
        try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = admin.createStatement();
                var rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE datname = current_database() AND pid <> pg_backend_pid()
                          AND backend_type = 'client backend'
                        """)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    private QueryResult query(DatabaseSession session, String sql) throws DatabaseException {
        return session.query(new QueryRequest(sql, 100, 4096, 5), new QueryCancellation());
    }

    @Test void returnsBoundedRowsCellsAndDistinctNullsWithRealCursorQueries() throws Exception {
        try (var session = connect()) {
            var result = session.query(new QueryRequest("SELECT generate_series(1,1000000) AS n", 3, 10, 5),
                    new QueryCancellation());
            assertEquals(3, result.rows().size());
            assertTrue(result.truncated());
            assertEquals("n", result.columns().getFirst().label());
            var cells = query(session, "SELECT NULL::text AS nil, ''::text AS empty, 'NULL'::text AS literal");
            assertTrue(cells.rows().getFirst().getFirst().isNull());
            assertEquals("", cells.rows().getFirst().get(1).value());
            assertFalse(cells.rows().getFirst().get(2).isNull());
            var clipped = session.query(new QueryRequest("SELECT repeat('x',10000000)", 1, 10, 5), new QueryCancellation());
            assertEquals(10, clipped.rows().getFirst().getFirst().value().length());
            assertTrue(clipped.rows().getFirst().getFirst().truncated());
            assertTrue(clipped.truncated());
            assertEquals("on", query(session, "SELECT current_setting('transaction_read_only')").rows().getFirst().getFirst().value());
        }
    }

    @Test void driverSplitterHandlesCommentsDollarQuotesEscapesAndJsonOperators() throws Exception {
        try (var session = connect()) {
            assertEquals("; DROP TABLE hidden;", query(session,
                    "/* outer /* nested */ */ SELECT $tag$; DROP TABLE hidden;$tag$ AS value; -- tail").rows().getFirst().getFirst().value());
            assertEquals("true", query(session, "SELECT '{\"a\":1}'::jsonb ? 'a'").rows().getFirst().getFirst().value());
            assertEquals("it's; fine", query(session, "SELECT E'it\\'s; fine'").rows().getFirst().getFirst().value());
        }
    }

    @Test void blocksScriptsDdlDmlAndTransactionControlsBeforeExecution() throws Exception {
        try (var session = connect()) {
            for (var sql : new String[] {"SELECT 1; DELETE FROM \"craft_%\".query_guard",
                    "COMMIT", "SET TRANSACTION READ WRITE", "CREATE TABLE public.forbidden(id int)",
                    "INSERT INTO \"craft_%\".query_guard VALUES (1)", "-- only comment"}) {
                assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> query(session, sql)).kind());
            }
            assertEquals("0", query(session, "SELECT count(*) FROM \"craft_%\".query_guard").rows().getFirst().getFirst().value());
        }
    }

    @Test void serverReadOnlyBlocksCteWritesAndFunctionWritesAndRecovers() throws Exception {
        try (var session = connect()) {
            for (var sql : new String[] {
                    "WITH changed AS (INSERT INTO \"craft_%\".query_guard VALUES (1) RETURNING id) SELECT * FROM changed",
                    "SELECT \"craft_%\".write_from_select()"}) {
                assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> query(session, sql)).kind());
            }
            assertThrows(DatabaseException.class, () -> query(session, "SELECT set_config('transaction_read_only','off',true)"));
            assertEquals("0", query(session, "SELECT count(*) FROM \"craft_%\".query_guard").rows().getFirst().getFirst().value());
            assertEquals("on", query(session, "SELECT current_setting('transaction_read_only')").rows().getFirst().getFirst().value());
        }
    }

    @Test void timedOutQueriesRollBackAndAllowSubsequentReads() throws Exception {
        try (var session = connect()) {
            var failure = assertThrows(DatabaseException.class, () -> session.query(
                    new QueryRequest("SELECT pg_sleep(10)", 1, 10, 1), new QueryCancellation()));
            assertEquals(DatabaseException.Kind.TIMEOUT, failure.kind());
            assertEquals("1", query(session, "SELECT 1").rows().getFirst().getFirst().value());
        }
    }

    @Test void cancellationInterruptsRunningQueryAndSessionCanBeReused() throws Exception {
        try (var session = connect(); var worker = Executors.newSingleThreadExecutor()) {
            var token = new QueryCancellation();
            var pending = worker.submit(() -> session.query(new QueryRequest("SELECT pg_sleep(30)", 1, 10, 40), token));
            boolean running = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                while (System.nanoTime() < deadline && !running) {
                    try (var statement = admin.createStatement(); var rows = statement.executeQuery("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE state = 'active' AND pid <> pg_backend_pid() AND query LIKE '%pg_sleep(30)%'
                            """)) { rows.next(); running = rows.getInt(1) > 0; }
                    if (!running) Thread.sleep(25);
                }
            }
            assertTrue(running, "Query must be active before testing cancellation.");
            token.cancel();
            var failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
            assertEquals(DatabaseException.Kind.CANCELLED, ((DatabaseException) failure.getCause()).kind());
            assertEquals("1", query(session, "SELECT 1").rows().getFirst().getFirst().value());
        }
    }

    @Test void failuresAndPreCancellationDoNotPoisonSession() throws Exception {
        try (var session = connect()) {
            assertThrows(DatabaseException.class, () -> query(session, "SELECT nonexistent_column"));
            var signal = new QueryCancellation();
            signal.cancel();
            assertEquals(DatabaseException.Kind.CANCELLED, assertThrows(DatabaseException.class,
                    () -> session.query(new QueryRequest("SELECT 1", 1, 10, 5), signal)).kind());
            assertEquals("1", query(session, "SELECT 1").rows().getFirst().getFirst().value());
        }
    }

    @Test void boundsTotalDisplayMemoryAndWideResultsAndPreservesDuplicateLabels() throws Exception {
        try (var session = connect()) {
            var bounded = session.query(new QueryRequest("SELECT repeat('x',4096) FROM generate_series(1,1000)",
                    1000, 4096, 5), new QueryCancellation());
            assertTrue(bounded.truncated());
            assertEquals(488, bounded.rows().size());
            assertTrue(bounded.rows().stream().mapToInt(row -> row.getFirst().value().length()).sum() <= 2_000_000);
            var wide = java.util.stream.IntStream.range(0, 129).mapToObj(i -> "1 AS c" + i)
                    .collect(Collectors.joining(",", "SELECT ", ""));
            assertEquals(DatabaseException.Kind.RESULT_LIMIT,
                    assertThrows(DatabaseException.class, () -> query(session, wide)).kind());
            var duplicates = query(session, "SELECT 1 AS duplicate, 2 AS duplicate");
            assertEquals("1", duplicates.rows().getFirst().getFirst().value());
            assertEquals("2", duplicates.rows().getFirst().get(1).value());
            var unicode = session.query(new QueryRequest("SELECT repeat('😀',10)", 1, 3, 5), new QueryCancellation());
            assertEquals("😀", unicode.rows().getFirst().getFirst().value());
        }
    }
}
