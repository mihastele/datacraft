/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.Objects;

public record QueryColumn(String label, String databaseType) {
    public QueryColumn {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(databaseType, "databaseType");
    }
}
