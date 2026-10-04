/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.datacraft.mysql.testing.MySqlTestDatabase;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class MySqlIntegrationTest {
    private static final Map<DatabaseKind, MySqlTestDatabase> DATABASES = new EnumMap<>(DatabaseKind.class);
    private final MySqlMariaDbAdapter adapter = new MySqlMariaDbAdapter();
    @BeforeAll static void provision() throws Exception {
        try {
            for (var kind : List.of(DatabaseKind.MYSQL, DatabaseKind.MARIADB)) {
                var database = new MySqlTestDatabase(kind); DATABASES.put(kind, database); database.start();
            }
        } catch (Exception failure) { DATABASES.values().forEach(MySqlTestDatabase::close); throw failure; }
    }
    @AfterAll static void cleanup() { DATABASES.values().forEach(MySqlTestDatabase::close); }
    private DatabaseSession open(DatabaseKind kind) throws Exception {
        var database = DATABASES.get(kind); char[] password = database.password();
        try { return adapter.connect(database.settings(TlsMode.DISABLED), password); }
        finally { Arrays.fill(password, '\0'); }
    }
    private static QueryResult query(DatabaseSession session, String sql) throws Exception {
        return session.query(new QueryRequest(sql, 100, 4096, 5), new QueryCancellation());
    }
    private static String value(QueryResult result) { return result.rows().getFirst().getFirst().value(); }

    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void discoversDatabasesTablesViewsAndNativeColumns(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            assertTrue(session.ping()); assertTrue(session.listSchemas().containsAll(List.of("datacraft_test", "craft_%")));
            assertFalse(session.listSchemas().contains("mysql"));
            var relations = session.listRelations("datacraft_test");
            assertTrue(relations.contains(new RelationMetadata(new QualifiedName("datacraft_test", "sample_view"), RelationKind.VIEW)));
            var columns = session.describeColumns(new QualifiedName("datacraft_test", "Odd' Table"));
            assertEquals(4, columns.size()); assertFalse(columns.getFirst().nullable());
            assertTrue(columns.getFirst().databaseType().toLowerCase().contains("unsigned"));
            assertEquals("Camel Column", columns.get(1).name()); assertTrue(columns.get(1).nullable());
            assertEquals("computed", columns.get(3).name());
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void missingObjectsAndCatalogParametersCannotInject(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            assertTrue(session.listRelations("datacraft_test' OR 1=1 -- ").isEmpty());
            assertEquals(DatabaseException.Kind.NOT_FOUND, assertThrows(DatabaseException.class,
                    () -> session.describeColumns(new QualifiedName("datacraft_test", "x' OR 1=1 -- "))).kind());
            assertTrue(session.ping());
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void structuredDatabaseSelectionPreservesLiteralPercentUnderscore(DatabaseKind kind) throws Exception {
        var database = DATABASES.get(kind); var settings = database.settings(TlsMode.DISABLED);
        char[] password = database.password();
        try (var session = adapter.connect(new ConnectionSettings(kind, settings.host(), settings.port(), "craft_%",
                settings.username(), settings.environment(), settings.tlsMode(), 5), password)) {
            assertEquals("craft_%", value(query(session, "SELECT DATABASE()")));
        } finally { Arrays.fill(password, '\0'); }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void preservesDuplicateLabelsNullEmptyAndLiteralNull(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            var result = query(session, "SELECT `Camel Column` AS label, `Camel Column` AS label FROM `Odd' Table` ORDER BY id");
            assertEquals(List.of("label", "label"), result.columns().stream().map(QueryColumn::label).toList());
            assertEquals("alpha", value(result)); assertTrue(result.rows().get(1).getFirst().isNull());
            assertEquals("", result.rows().get(2).getFirst().value()); assertEquals("NULL", result.rows().get(3).getFirst().value());
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void enforcesRowsCellsAggregateAndColumnBudgets(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            var result = session.query(new QueryRequest("SELECT `Camel Column` FROM `Odd' Table` ORDER BY id", 1, 3, 5), new QueryCancellation());
            assertEquals("alp", value(result)); assertEquals(1, result.rows().size()); assertTrue(result.truncated());
            var unicode = session.query(new QueryRequest("SELECT 'a😀b'", 1, 2, 5), new QueryCancellation());
            assertEquals("a", value(unicode)); assertTrue(unicode.truncated());
            var budget = session.query(new QueryRequest("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<1000) SELECT REPEAT('x',4096) FROM n", 1000, 4096, 5), new QueryCancellation());
            assertEquals(488, budget.rows().size()); assertTrue(budget.truncated());
            String wide = "SELECT " + String.join(",", Collections.nCopies(129, "1"));
            assertEquals(DatabaseException.Kind.RESULT_LIMIT, assertThrows(DatabaseException.class, () -> query(session, wide)).kind());
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void acceptsNativeQuotingCommentsCtesAndDoesNotShadowRelations(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            assertEquals("semi;colon", value(query(session, "# header\nSELECT 'semi;colon'; -- tail")));
            assertEquals("a'b", value(query(session, "SELECT 'a''b' AS `quoted``name`")));
            assertEquals("ordinary relation", value(query(session, "SELECT value FROM dc")));
            assertEquals("3", value(query(session, "SELECT 1--2")));
            assertEquals("42", value(query(session, "WITH n AS (SELECT 42 AS answer) SELECT answer FROM n")));
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void blocksWritesControlsScriptsExecutableCommentsAndFileOutput(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            for (String sql : List.of("DELETE FROM dc", "CREATE TABLE bad(x INT)", "SET TRANSACTION READ WRITE", "COMMIT",
                    "SELECT 1; DELETE FROM dc", "SELECT /*! 1 */", "SELECT /*M! 1 */", "SELECT 1 INTO OUTFILE '/tmp/no'", "SELECT ?")) {
                assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> query(session, sql), sql).kind());
            }
            assertEquals("4", value(query(session, "SELECT count(*) FROM `Odd' Table`")));
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void serverReadOnlyTransactionBlocksFunctionWrites(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> query(session, "SELECT write_from_select()" )).kind());
            assertEquals("0", value(query(session, "SELECT count(*) FROM query_guard")));
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void timesOutAndRecovers(DatabaseKind kind) throws Exception {
        try (var session = open(kind)) {
            assertEquals(DatabaseException.Kind.TIMEOUT, assertThrows(DatabaseException.class,
                    () -> session.query(new QueryRequest("SELECT SLEEP(30)", 1, 16, 1), new QueryCancellation())).kind());
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void cancelsActiveServerQueryAndRecovers(DatabaseKind kind) throws Exception {
        try (var session = open(kind); var executor = Executors.newSingleThreadExecutor()) {
            var signal = new QueryCancellation();
            var future = executor.submit(() -> session.query(new QueryRequest("SELECT SLEEP(30)", 1, 16, 30), signal));
            boolean running = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try (var admin = DATABASES.get(kind).admin()) {
                while (!running && System.nanoTime() < deadline) {
                    try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM information_schema.PROCESSLIST WHERE USER='dc_reader' AND COMMAND='Query' AND INFO LIKE '%SLEEP(30)%'")) {
                        rows.next(); running = rows.getInt(1) > 0;
                    }
                    if (!running) Thread.sleep(25);
                }
            }
            assertTrue(running); signal.cancel();
            var failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(DatabaseException.Kind.CANCELLED, ((DatabaseException) failure.getCause()).kind());
            assertEquals("42", value(query(session, "SELECT 42")));
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void authenticationTlsAndCallerOwnershipAreExplicit(DatabaseKind kind) throws Exception {
        var database = DATABASES.get(kind); char[] wrong = UUID.randomUUID().toString().toCharArray(); char[] before = wrong.clone();
        assertEquals(DatabaseException.Kind.AUTHENTICATION, assertThrows(DatabaseException.class,
                () -> adapter.connect(database.settings(TlsMode.DISABLED), wrong)).kind());
        assertArrayEquals(before, wrong); Arrays.fill(wrong, '\0'); Arrays.fill(before, '\0');
        char[] password = database.password();
        try {
            if (kind == DatabaseKind.MYSQL) {
                assertThrows(DatabaseException.class, () -> adapter.connect(database.settings(TlsMode.VERIFY_FULL), password));
            } else {
                try (var session = adapter.connect(database.settings(TlsMode.VERIFY_FULL), password)) {
                    // MariaDB 11.4+ verifies its generated certificate fingerprint through password authentication.
                    assertFalse(value(query(session, "SELECT VARIABLE_VALUE FROM information_schema.SESSION_STATUS WHERE VARIABLE_NAME='Ssl_cipher'")).isBlank());
                }
                char[] tlsWrong = UUID.randomUUID().toString().toCharArray();
                try { assertThrows(DatabaseException.class, () -> adapter.connect(database.settings(TlsMode.VERIFY_FULL), tlsWrong)); }
                finally { Arrays.fill(tlsWrong, '\0'); }
            }
        }
        finally { Arrays.fill(password, '\0'); }
    }
    @Test void verifiedTlsNeverDowngradesWhenServerDisablesTls() throws Exception {
        try (var database = new MySqlTestDatabase(DatabaseKind.MARIADB, false)) {
            database.start(); char[] password = database.password();
            try {
                assertThrows(DatabaseException.class, () -> adapter.connect(database.settings(TlsMode.VERIFY_FULL), password));
                try (var session = adapter.connect(database.settings(TlsMode.DISABLED), password)) { assertTrue(session.ping()); }
            } finally { Arrays.fill(password, '\0'); }
        }
    }
    @ParameterizedTest @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    void errorAndPreCancellationRecoveryCloseAndTransactionRelease(DatabaseKind kind) throws Exception {
        var session = open(kind);
        try {
            var signal = new QueryCancellation(); signal.cancel();
            assertEquals(DatabaseException.Kind.CANCELLED, assertThrows(DatabaseException.class,
                    () -> session.query(new QueryRequest("SELECT 1", 1, 1, 1), signal)).kind());
            assertThrows(DatabaseException.class, () -> query(session, "SELECT missing FROM absent"));
            query(session, "SELECT 42"); session.listSchemas();
            try (var admin = DATABASES.get(kind).admin(); var statement = admin.createStatement();
                    var rows = statement.executeQuery("SELECT count(*) FROM information_schema.INNODB_TRX")) {
                rows.next(); assertEquals(0, rows.getInt(1));
            }
        } finally { session.close(); session.close(); }
        assertEquals(DatabaseException.Kind.CLOSED, assertThrows(DatabaseException.class, session::ping).kind());
    }
}
