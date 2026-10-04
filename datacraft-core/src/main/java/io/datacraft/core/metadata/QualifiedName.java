/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.metadata;

import java.util.Objects;

/** Exact identifiers; case, whitespace, and punctuation must not be normalized. */
public record QualifiedName(String schema, String name) {
    public QualifiedName {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(name, "name");
        if (schema.isEmpty() || name.isEmpty() || schema.indexOf('\0') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid object identifier.");
        }
    }
}
