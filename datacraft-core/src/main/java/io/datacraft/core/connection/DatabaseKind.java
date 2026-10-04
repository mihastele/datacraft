/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

public enum DatabaseKind {
    POSTGRESQL("PostgreSQL"), SQLITE("SQLite"), MYSQL("MySQL"), MARIADB("MariaDB");
    private final String label;
    DatabaseKind(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
