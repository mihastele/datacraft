/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sqlite;

import io.datacraft.core.adapter.DatabaseException;
import io.datacraft.core.query.ReadOnlyQueryPolicy;

/** SQLite lexical statement boundary only; the engine validates SELECT grammar in a subquery. */
final class SqliteQueryPolicy {
    private SqliteQueryPolicy() { }
    static String singleSelect(String sql) throws DatabaseException {
        int index = trivia(sql, 0);
        int start = index;
        while (index < sql.length() && Character.isLetter(sql.charAt(index))) index++;
        String first = sql.substring(start, index);
        ReadOnlyQueryPolicy.requireSingleSelect(1, first.equalsIgnoreCase("SELECT") || first.equalsIgnoreCase("WITH"));
        while (index < sql.length()) {
            index = trivia(sql, index);
            if (index == sql.length()) break;
            char current = sql.charAt(index++);
            if (current == ';') {
                if (trivia(sql, index) != sql.length()) deny();
                return sql.substring(0, index - 1);
            }
            if (current == '\0' || current == '?' || current == ':' || current == '@' || current == '$') deny();
            if (current == '\'' || current == '"' || current == '`' || current == '[') {
                char closing = current == '[' ? ']' : current;
                boolean closed = false;
                while (index < sql.length()) {
                    if (sql.charAt(index++) != closing) continue;
                    if (current != '[' && index < sql.length() && sql.charAt(index) == closing) { index++; continue; }
                    closed = true; break;
                }
                if (!closed) deny();
            }
        }
        return sql;
    }
    private static int trivia(String sql, int index) throws DatabaseException {
        while (index < sql.length()) {
            if (Character.isWhitespace(sql.charAt(index))) { index++; continue; }
            if (sql.startsWith("--", index)) {
                while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') index++;
            } else if (sql.startsWith("/*", index)) {
                int end = sql.indexOf("*/", index + 2);
                if (end < 0) deny();
                index = end + 2;
            } else break;
        }
        return index;
    }
    private static void deny() throws DatabaseException { throw new DatabaseException(DatabaseException.Kind.POLICY); }
}
