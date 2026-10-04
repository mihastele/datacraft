/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.ArrayList;
import java.util.List;
import io.datacraft.core.connection.DatabaseKind;

/** Small tolerant cursor lexer, not a SQL parser or an execution policy. UTF-16 offsets. */
final class CompletionTokens {
    record Token(String text, int start, int end, boolean identifier) {}
    record Cursor(List<Token> tokens, boolean suppressed) {}
    private CompletionTokens() {}
    static Cursor scan(String sql, int caret, DatabaseKind kind) {
        var tokens = new ArrayList<Token>();
        boolean mysql = kind == DatabaseKind.MYSQL || kind == DatabaseKind.MARIADB;
        for (int i = 0; i < sql.length();) {
            int start = i;
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if ((c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-'
                    && (!mysql || i + 2 == sql.length() || Character.isWhitespace(sql.charAt(i + 2))))
                    || (mysql && c == '#')) {
                while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') i++;
                if (caret > start && caret <= i) return new Cursor(List.of(), true);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int depth = 1; i += 2;
                while (i < sql.length() && depth > 0) {
                    if (sql.startsWith("*/", i)) { depth--; i += 2; }
                    else if (kind == DatabaseKind.POSTGRESQL && sql.startsWith("/*", i)) { depth++; i += 2; }
                    else i++;
                }
                if (caret > start && (caret < i || depth > 0 && caret == i)) return new Cursor(List.of(), true);
                continue;
            }
            if (c == '$' && kind == DatabaseKind.POSTGRESQL) {
                int tagEnd = sql.indexOf('$', i + 1);
                if (tagEnd >= 0 && sql.substring(i + 1, tagEnd).matches("[A-Za-z_][A-Za-z_0-9]*|")) {
                    String tag = sql.substring(i, tagEnd + 1);
                    int close = sql.indexOf(tag, tagEnd + 1);
                    i = close < 0 ? sql.length() : close + tag.length();
                    if (caret > start && (caret < i || close < 0 && caret == i)) return new Cursor(List.of(), true);
                    continue;
                }
            }
            if (c == '\'' || c == '"' || c == '`' || c == '[' && kind == DatabaseKind.SQLITE) {
                char end = c == '[' ? ']' : c; i++;
                boolean escaped = c == '\'' && kind == DatabaseKind.POSTGRESQL && start > 0
                        && (sql.charAt(start - 1) == 'E' || sql.charAt(start - 1) == 'e')
                        && (start < 2 || !Character.isJavaIdentifierPart(sql.charAt(start - 2)));
                boolean closed = false;
                while (i < sql.length()) {
                    if (escaped && sql.charAt(i) == '\\') { i = Math.min(sql.length(), i + 2); continue; }
                    if (sql.charAt(i++) == end) {
                        if (i < sql.length() && sql.charAt(i) == end) i++;
                        else { closed = true; break; }
                    }
                }
                boolean string = c == '\'' || mysql && c == '"';
                if (caret > start && (caret < i || !closed && caret == i)) return new Cursor(List.of(), true);
                if (!string) tokens.add(new Token(sql.substring(start, i), start, i, true));
                else tokens.add(new Token("", start, i, false));
                continue;
            }
            if (Character.isLetter(c) || c == '_' || Character.isDigit(c)) {
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || "_$".indexOf(sql.charAt(i)) >= 0)) i++;
                tokens.add(new Token(sql.substring(start, i), start, i, true));
            } else {
                i++; tokens.add(new Token(sql.substring(start, i), start, i, false));
            }
        }
        return new Cursor(List.copyOf(tokens), false);
    }
}
