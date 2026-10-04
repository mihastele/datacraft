/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import io.datacraft.core.adapter.DatabaseException;
import io.datacraft.core.query.ReadOnlyQueryPolicy;

/** Lexical gate for sessions pinned to NO_BACKSLASH_ESCAPES, not a semantic parser. */
final class MySqlQueryPolicy {
    private MySqlQueryPolicy() { }
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
            if (current == '\0' || current == '?' || current == '@') deny();
            if (current == '\'' || current == '"' || current == '`') {
                boolean closed = false;
                while (index < sql.length()) {
                    if (sql.charAt(index++) != current) continue;
                    if (index < sql.length() && sql.charAt(index) == current) { index++; continue; }
                    closed = true; break;
                }
                if (!closed) deny();
            } else if (Character.isLetter(current) || current == '_') {
                int wordStart = index - 1;
                while (index < sql.length() && (Character.isLetterOrDigit(sql.charAt(index)) || sql.charAt(index) == '_')) index++;
                if (sql.substring(wordStart, index).equalsIgnoreCase("INTO")) deny();
            }
        }
        return sql;
    }
    private static int trivia(String sql, int index) throws DatabaseException {
        while (index < sql.length()) {
            if (Character.isWhitespace(sql.charAt(index))) { index++; continue; }
            if (sql.charAt(index) == '#' || (sql.startsWith("--", index)
                    && (index + 2 == sql.length() || Character.isWhitespace(sql.charAt(index + 2))))) {
                while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') index++;
            } else if (sql.startsWith("/*", index)) {
                if (sql.startsWith("/*!", index) || sql.startsWith("/*+", index)
                        || sql.regionMatches(true, index, "/*M!", 0, 4)) deny();
                int end = sql.indexOf("*/", index + 2);
                if (end < 0 || (sql.indexOf("/*", index + 2) >= 0 && sql.indexOf("/*", index + 2) < end)) deny();
                index = end + 2;
            } else break;
        }
        return index;
    }
    private static void deny() throws DatabaseException { throw new DatabaseException(DatabaseException.Kind.POLICY); }
}
