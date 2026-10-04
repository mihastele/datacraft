/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.Objects;

/** One read query. SQL is sensitive session data and must not be logged. */
public record QueryRequest(String sql, int rowLimit, int cellCharacterLimit, int timeoutSeconds) {
    public QueryRequest {
        Objects.requireNonNull(sql, "sql");
        if (sql.isBlank() || sql.length() > 100_000) {
            throw new IllegalArgumentException("SQL must contain between 1 and 100000 characters.");
        }
        if (rowLimit < 1 || rowLimit > 1000) throw new IllegalArgumentException("Row limit must be 1–1000.");
        if (cellCharacterLimit < 1 || cellCharacterLimit > 4096) {
            throw new IllegalArgumentException("Cell limit must be 1–4096 characters.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("Query timeout must be 1–300 seconds.");
        }
    }

    @Override public String toString() { return "QueryRequest[SQL omitted]"; }
}
