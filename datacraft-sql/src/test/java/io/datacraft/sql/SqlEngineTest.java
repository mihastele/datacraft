/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.List;
import java.util.Map;
import io.datacraft.core.connection.DatabaseKind;
import io.datacraft.core.metadata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class SqlEngineTest {
    private static final QualifiedName USERS = new QualifiedName("public", "users");
    private static final SchemaSnapshot SCHEMA = new SchemaSnapshot(List.of("public"),
            List.of(new RelationMetadata(USERS, RelationKind.TABLE)),
            Map.of(USERS, List.of(new ColumnMetadata("email", 1, "text", true), new ColumnMetadata("id", 2, "integer", false))));
    @ParameterizedTest @EnumSource(DatabaseKind.class) void parsesRealAstAndCompletesAliasForEveryDatabase(DatabaseKind kind) {
        try (var engine = new SqlEngine()) {
            var analysis = engine.analyze("SELECT u.email FROM public.users u WHERE u.id = 1", kind);
            assertTrue(analysis.diagnostics().isEmpty());
            assertTrue(flatten(analysis.statements()).contains("Column"));
            String sql = "SELECT u.email FROM public.users u WHERE u.";
            var completion = engine.complete(sql, sql.length(), kind, SCHEMA);
            assertEquals(List.of("email", "id"), completion.items().stream().map(SqlCompletion.Item::label).sorted().toList());
        }
    }
    @Test void replacesEntirePrefixAndPreservesSuffix() {
        try (var engine = new SqlEngine()) {
            String sql = "SELECT u.emxx FROM public.users u";
            int caret = sql.indexOf("emxx") + 2;
            var completion = engine.complete(sql, caret, DatabaseKind.POSTGRESQL, SCHEMA);
            assertEquals(sql.indexOf("emxx"), completion.start());
            assertEquals(sql.indexOf("emxx") + 4, completion.end());
            assertEquals("\"email\"", completion.items().getFirst().insertText());
        }
    }
    @Test void completesRelationsWithoutParsingIncompleteFromClause() {
        try (var engine = new SqlEngine()) {
            var completion = engine.complete("SELECT * FROM public.us", 23, DatabaseKind.POSTGRESQL, SCHEMA);
            assertEquals("\"users\"", completion.items().getFirst().insertText());
        }
    }
    @Test void doesNotLeakAliasesBetweenStatementsOrNestedScopes() {
        try (var engine = new SqlEngine()) {
            for (String sql : List.of("SELECT * FROM public.users u; SELECT u.",
                    "SELECT * FROM public.users u WHERE EXISTS (SELECT x. FROM public.users x)")) {
                int caret = sql.contains("x.") ? sql.indexOf("x.") + 2 : sql.length();
                var result = engine.complete(sql, caret, DatabaseKind.POSTGRESQL, SCHEMA);
                if (sql.contains("x.")) assertEquals(2, result.items().size());
                else assertTrue(result.items().isEmpty());
            }
        }
    }
    @Test void doesNotGuessSearchPathOrInventUnloadedColumns() {
        var duplicate = new SchemaSnapshot(List.of("public", "other"),
                List.of(new RelationMetadata(USERS, RelationKind.TABLE), new RelationMetadata(new QualifiedName("other", "users"), RelationKind.TABLE)), SCHEMA.columns());
        try (var engine = new SqlEngine()) {
            String sql = "SELECT u. FROM users u";
            assertTrue(engine.complete(sql, 9, DatabaseKind.POSTGRESQL, duplicate).items().isEmpty());
            assertTrue(engine.complete(sql, 9, DatabaseKind.POSTGRESQL, SchemaSnapshot.EMPTY).items().isEmpty());
        }
    }
    @Test void ignoresStringsCommentsAndPostgresDollarBodies() {
        try (var engine = new SqlEngine()) {
            for (String sql : List.of("SELECT 'u.", "SELECT 1 -- u.", "SELECT /* outer /* inner */ u.", "SELECT $tag$u.",
                    "SELECT E'escaped\\' FROM public.users u WHERE u."))
                assertTrue(engine.complete(sql, sql.length(), DatabaseKind.POSTGRESQL, SCHEMA).items().isEmpty());
            String mysql = "SELECT 1 # u.";
            assertTrue(engine.complete(mysql, mysql.length(), DatabaseKind.MYSQL, SCHEMA).items().isEmpty());
        }
    }
    @Test void honorsQuotedIdentifiersAndEscapesCompletionInsertion() {
        var exact = new QualifiedName("public", "Mixed");
        var snapshot = new SchemaSnapshot(List.of("public"), List.of(new RelationMetadata(exact, RelationKind.TABLE)),
                Map.of(exact, List.of(new ColumnMetadata("a\"b", 1, "text", true))));
        try (var engine = new SqlEngine()) {
            String sql = "SELECT m. FROM public.\"Mixed\" m";
            assertEquals("\"a\"\"b\"", engine.complete(sql, 9, DatabaseKind.POSTGRESQL, snapshot).items().getFirst().insertText());
            assertTrue(engine.complete("SELECT m. FROM public.Mixed m", 9, DatabaseKind.POSTGRESQL, snapshot).items().isEmpty());
        }
    }
    @Test void syntaxErrorsAreAdvisoryAndDoNotExposeLiteralText() {
        try (var engine = new SqlEngine()) {
            String sql = "SELECT 1;\nSELECT FROM";
            var result = engine.analyze(sql, DatabaseKind.POSTGRESQL);
            assertFalse(result.diagnostics().isEmpty());
            assertTrue(result.statements().isEmpty());
            assertTrue(result.diagnostics().getFirst().offset() <= sql.length());
            assertFalse(result.diagnostics().getFirst().message().contains(sql));
            assertTrue(engine.analyze(" ", DatabaseKind.SQLITE).statements().isEmpty());
        }
    }
    @Test void limitsInputAndAstAndRecoversOnNextRequest() {
        try (var engine = new SqlEngine()) {
            assertFalse(engine.analyze("x".repeat(100001), DatabaseKind.POSTGRESQL).diagnostics().isEmpty());
            assertTrue(engine.complete("x".repeat(100001), 0, DatabaseKind.SQLITE, SCHEMA).items().isEmpty());
            assertTrue(engine.analyze("SELECT 1", DatabaseKind.SQLITE).diagnostics().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> engine.complete("", 1, DatabaseKind.SQLITE, SCHEMA));
        }
    }
    @Test void doesNotTreatCteOrDerivedTablesAsRealRelations() {
        try (var engine = new SqlEngine()) {
            String sql = "WITH users AS (SELECT 1 AS invented) SELECT u. FROM users u";
            assertTrue(engine.complete(sql, sql.indexOf("u.") + 2, DatabaseKind.POSTGRESQL, SCHEMA).items().isEmpty());
            sql = "SELECT u. FROM (SELECT 1 AS invented) u";
            assertTrue(engine.complete(sql, 9, DatabaseKind.POSTGRESQL, SCHEMA).items().isEmpty());
        }
    }
    @Test void astRangesUseExactUtf16OffsetsWithoutIncludingNextCharacter() {
        try (var engine = new SqlEngine()) {
            String sql = "SELECT u.email FROM public.users u WHERE u.id = 1";
            var analysis = engine.analyze(sql, DatabaseKind.POSTGRESQL);
            assertTrue(hasRange(analysis.statements(), sql, "u.email"));
            assertTrue(hasRange(analysis.statements(), sql, "u.id"));
            sql = "SELECT '😀' AS face,\r\n\tu.email FROM public.users u";
            analysis = engine.analyze(sql, DatabaseKind.POSTGRESQL);
            assertTrue(analysis.diagnostics().isEmpty());
            assertTrue(hasRange(analysis.statements(), sql, "u.email"));
        }
    }
    private static boolean hasRange(List<SqlAnalysis.AstNode> nodes, String sql, String expected) {
        return nodes.stream().anyMatch(node -> sql.substring(node.start(), node.end()).equals(expected)
                || hasRange(node.children(), sql, expected));
    }
    private static String flatten(List<SqlAnalysis.AstNode> nodes) {
        var result = new StringBuilder();
        for (var node : nodes) result.append(node.kind()).append(flatten(node.children()));
        return result.toString();
    }
}
