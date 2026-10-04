/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.metadata;

import java.util.Objects;

/** databaseType is the adapter's display type, not a JDBC code or normalized type hierarchy. */
public record ColumnMetadata(String name, int ordinal, String databaseType, boolean nullable) {
    public ColumnMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(databaseType, "databaseType");
        if (name.isEmpty() || databaseType.isEmpty() || ordinal < 1) {
            throw new IllegalArgumentException("Invalid column metadata.");
        }
    }
}
