/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.metadata;

import java.util.Objects;

public record RelationMetadata(QualifiedName name, RelationKind kind) {
    public RelationMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }
}
