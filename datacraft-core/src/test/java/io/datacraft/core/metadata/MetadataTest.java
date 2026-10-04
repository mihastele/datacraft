/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.metadata;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetadataTest {
    @Test void quotedIdentifiersArePreservedExactly() {
        var name = new QualifiedName("craft_%", "  Case ' Quoted  ");
        assertEquals("craft_%", name.schema());
        assertEquals("  Case ' Quoted  ", name.name());
        assertNotEquals(name, new QualifiedName("craft_%", "case ' quoted"));
    }
    @Test void rejectsUnrepresentableIdentifiersAndOrdinals() {
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName("", "table"));
        assertThrows(IllegalArgumentException.class, () -> new QualifiedName("public", "a\0b"));
        assertThrows(IllegalArgumentException.class, () -> new ColumnMetadata("id", 0, "integer", false));
    }
}
