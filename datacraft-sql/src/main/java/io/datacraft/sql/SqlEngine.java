/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.*;
import java.util.concurrent.*;
import io.datacraft.core.connection.DatabaseKind;
import io.datacraft.core.metadata.*;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.*;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.schema.Table;

/** Advisory SQL intelligence. Never executes SQL and never replaces adapter safety policies. */
public final class SqlEngine implements AutoCloseable {
    private static final String PLACEHOLDER = "__datacraft_completion__";
    private static final List<String> KEYWORDS = List.of("SELECT", "FROM", "WHERE", "JOIN", "LEFT JOIN", "ON",
            "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "WITH", "AS", "DISTINCT", "AND", "OR", "IS NULL");
    private final ExecutorService parserWorker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "datacraft-sql-parser"); thread.setDaemon(true); return thread;
    });

    public SqlAnalysis analyze(String sql, DatabaseKind kind) {
        Objects.requireNonNull(sql); Objects.requireNonNull(kind);
        if (sql.length() > 100_000) return failure(0, 1, 1, "Analysis limited to 100000 characters.");
        if (sql.isBlank()) return new SqlAnalysis(List.of(), List.of());
        try {
            Node root = parse(sql, kind);
            int[] budget = {2000};
            return new SqlAnalysis(List.of(project(root, sql.length(), budget, 0)), List.of());
        } catch (JSQLParserException | IllegalStateException failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && !(cause instanceof ParseException)) cause = cause.getCause();
            if (cause instanceof ParseException error && error.currentToken != null) {
                Token token = error.currentToken.next == null ? error.currentToken : error.currentToken.next;
                int offset = token.kind == 0 ? sql.length() : Math.clamp(token.absoluteBegin - 1, 0, sql.length());
                return failure(offset, Math.max(1, token.beginLine), Math.max(1, token.beginColumn),
                        "Incomplete SQL or syntax unsupported by the editor parser.");
            }
            return failure(0, 1, 1, "Analysis unavailable: unsupported syntax or parsing limit reached.");
        }
    }
    private static SqlAnalysis failure(int offset, int line, int column, String message) {
        return new SqlAnalysis(List.of(), List.of(new SqlAnalysis.Diagnostic(offset, line, column, message)));
    }
    private Node parse(String sql, DatabaseKind kind) throws JSQLParserException {
        var parser = CCJSqlParserUtil.newParser(sql).withTimeOut(500).withAllowedNestingDepth(32)
                .withAllowComplexParsing(false).withDialect(switch (kind) {
                    case POSTGRESQL -> AbstractJSqlParser.Dialect.POSTGRESQL;
                    case MYSQL -> AbstractJSqlParser.Dialect.MYSQL;
                    case MARIADB -> AbstractJSqlParser.Dialect.MARIADB;
                    case SQLITE -> AbstractJSqlParser.Dialect.ANSI_SQL;
                });
        // The adapters deliberately use NO_BACKSLASH_ESCAPES for both MySQL families.
        if (kind == DatabaseKind.MYSQL || kind == DatabaseKind.MARIADB) parser.withBackslashEscapeCharacter(false);
        if (kind == DatabaseKind.SQLITE) parser.withSquareBracketQuotation(true);
        CCJSqlParserUtil.parseStatements(parser, parserWorker);
        return parser.getASTRoot();
    }
    private static SqlAnalysis.AstNode project(Node node, int length, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 64) throw new IllegalStateException("AST display limit reached.");
        // JavaCC 8 exposes one-based absolute bounds with an exclusive end; EOF has zero bounds.
        int start = node.jjtGetFirstToken() == null ? 0 : Math.clamp(node.jjtGetFirstToken().absoluteBegin - 1, 0, length);
        int end = node.jjtGetLastToken() == null ? start : node.jjtGetLastToken().kind == 0 ? length
                : Math.clamp(node.jjtGetLastToken().absoluteEnd - 1, start, length);
        var children = new ArrayList<SqlAnalysis.AstNode>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) children.add(project(node.jjtGetChild(i), length, budget, depth + 1));
        Object value = node.jjtGetValue();
        String label = value == null ? node.toString() : value.getClass().getSimpleName();
        return new SqlAnalysis.AstNode(label, start, end, children);
    }

    public SqlCompletion complete(String sql, int caret, DatabaseKind kind, SchemaSnapshot schema) {
        Objects.requireNonNull(sql); Objects.requireNonNull(kind); Objects.requireNonNull(schema);
        if (caret < 0 || caret > sql.length()) throw new IllegalArgumentException("Invalid caret.");
        if (sql.length() > 100_000) return new SqlCompletion(caret, caret, List.of());
        var scanned = CompletionTokens.scan(sql, caret, kind);
        if (scanned.suppressed()) return new SqlCompletion(caret, caret, List.of());
        var tokens = scanned.tokens();
        int start = caret, end = caret;
        for (var token : tokens) if (token.identifier() && token.start() <= caret && token.end() >= caret) {
            start = token.start(); end = token.end(); break;
        }
        String prefix = sql.substring(start, caret);
        var before = new ArrayList<CompletionTokens.Token>();
        int statementStart = 0, statementEnd = sql.length();
        for (var token : tokens) {
            if (token.text().equals(";") && token.end() <= caret) { statementStart = token.end(); before.clear(); }
            else if (token.text().equals(";") && token.start() >= caret) { statementEnd = token.start(); break; }
            else if (token.end() <= start) before.add(token);
        }
        String qualifier = before.size() >= 2 && before.getLast().text().equals(".")
                ? before.get(before.size() - 2).text() : null;
        int previous = before.size() - (qualifier == null ? 1 : 3);
        String preceding = previous >= 0 ? before.get(previous).text().toUpperCase(Locale.ROOT) : "";
        boolean relationContext = preceding.equals("FROM") || preceding.equals("JOIN");
        var items = new TreeMap<String, SqlCompletion.Item>();
        if (relationContext) {
            for (var relation : schema.relations()) {
                if (qualifier != null && !matches(relation.name().schema(), qualifier, kind)) continue;
                String insertion = qualifier == null ? quote(relation.name().schema(), kind) + "." + quote(relation.name().name(), kind)
                        : quote(relation.name().name(), kind);
                add(items, prefix, relation.name().name(), insertion, relation.name().schema() + " · " + relation.kind());
            }
            if (qualifier == null) for (var name : schema.schemas()) add(items, prefix, name, quote(name, kind) + ".", "schema / database");
        } else {
            PlainSelect scope = null;
            var ctes = new HashSet<String>();
            try {
                String repaired = sql.substring(statementStart, start) + PLACEHOLDER + sql.substring(end, statementEnd);
                Node root = parse(repaired, kind);
                scope = scope(root, start - statementStart, 0);
                collectCtes(root, ctes, 0);
            } catch (JSQLParserException | IllegalStateException ignored) {
                // Incomplete/unsupported input cannot safely establish alias bindings.
            }
            if (scope != null) {
                var tables = new ArrayList<Table>();
                if (scope.getFromItem() instanceof Table table) tables.add(table);
                if (scope.getJoins() != null) for (var join : scope.getJoins()) if (join.getFromItem() instanceof Table table) tables.add(table);
                for (var table : tables) {
                    if (table.getSchemaName() == null && ctes.stream().anyMatch(name -> matchesIdentifier(name, table.getName(), kind))) continue;
                    String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
                    if (qualifier != null && !matchesIdentifier(alias, qualifier, kind)) continue;
                    var candidates = schema.relations().stream().map(RelationMetadata::name)
                            .filter(name -> matches(name.name(), table.getName(), kind)
                                    && (table.getSchemaName() == null || matches(name.schema(), table.getSchemaName(), kind))).toList();
                    // No search-path guess when the same unqualified table exists in multiple schemas.
                    if (candidates.size() != 1) continue;
                    for (var column : schema.columns().getOrDefault(candidates.getFirst(), List.of())) {
                        String insertion = (qualifier == null && tables.size() > 1 ? quote(unquote(alias), kind) + "." : "") + quote(column.name(), kind);
                        add(items, prefix, column.name(), insertion, alias + " · " + column.databaseType());
                    }
                }
            }
            if (qualifier == null) for (var keyword : KEYWORDS) add(items, prefix, keyword, keyword, "keyword");
        }
        return new SqlCompletion(start, end, items.values().stream().limit(100).toList());
    }
    private static PlainSelect scope(Node node, int caret, int depth) {
        if (depth > 64) return null;
        PlainSelect found = null;
        if (node.jjtGetValue() instanceof PlainSelect select && node.jjtGetFirstToken() != null && node.jjtGetLastToken() != null
                && node.jjtGetFirstToken().absoluteBegin - 1 <= caret && node.jjtGetLastToken().absoluteEnd - 1 >= caret) found = select;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            var child = scope(node.jjtGetChild(i), caret, depth + 1);
            if (child != null) found = child;
        }
        return found;
    }
    private static void collectCtes(Node node, Set<String> names, int depth) {
        if (depth > 64) return;
        if (node.jjtGetValue() instanceof Select select && select.getWithItemsList() != null)
            select.getWithItemsList().forEach(item -> names.add(item.getAliasName()));
        for (int i = 0; i < node.jjtGetNumChildren(); i++) collectCtes(node.jjtGetChild(i), names, depth + 1);
    }
    private static void add(Map<String, SqlCompletion.Item> items, String prefix, String label, String insertion, String detail) {
        if (label.toLowerCase(Locale.ROOT).startsWith(unquote(prefix).toLowerCase(Locale.ROOT)))
            items.put(insertion, new SqlCompletion.Item(label, insertion, detail));
    }
    private static boolean matchesIdentifier(String left, String right, DatabaseKind kind) {
        return matches(unquote(left), right, kind);
    }
    private static boolean matches(String actual, String sqlIdentifier, DatabaseKind kind) {
        if (quoted(sqlIdentifier)) return actual.equals(unquote(sqlIdentifier));
        return kind == DatabaseKind.POSTGRESQL ? actual.equals(sqlIdentifier.toLowerCase(Locale.ROOT))
                : kind == DatabaseKind.SQLITE ? actual.equalsIgnoreCase(sqlIdentifier) : actual.equals(sqlIdentifier);
    }
    private static boolean quoted(String value) { return !value.isEmpty() && "\"`[".indexOf(value.charAt(0)) >= 0; }
    private static String unquote(String value) {
        if (value.length() < 2 || !quoted(value)) return value;
        String end = value.startsWith("[") ? "]" : value.substring(0, 1);
        if (!value.endsWith(end)) return value;
        return value.substring(1, value.length() - 1).replace(end + end, end);
    }
    private static String quote(String identifier, DatabaseKind kind) {
        String mark = kind == DatabaseKind.MYSQL || kind == DatabaseKind.MARIADB ? "`" : "\"";
        return mark + identifier.replace(mark, mark + mark) + mark;
    }
    @Override public void close() { parserWorker.shutdownNow(); }
}
