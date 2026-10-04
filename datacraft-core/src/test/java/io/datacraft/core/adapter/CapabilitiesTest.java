/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {
    @Test
    void emptyDeclarationDoesNotClaimAnyFeature() {
        var capabilities = Capabilities.none();
        for (var capability : Capability.values()) {
            assertFalse(capabilities.supports(capability), capability.name());
        }
    }

    @Test
    void declarationSupportsOnlyExplicitFeatures() {
        var capabilities = new Capabilities(Set.of(Capability.TRANSACTIONS, Capability.SCHEMAS));
        for (var capability : Capability.values()) {
            assertEquals(capability == Capability.TRANSACTIONS || capability == Capability.SCHEMAS,
                    capabilities.supports(capability), capability.name());
        }
    }

    @Test
    void callerCannotChangeDeclarationAfterConstruction() {
        var source = EnumSet.of(Capability.TRANSACTIONS);
        var capabilities = new Capabilities(source);
        source.clear();
        source.add(Capability.EDITABLE_RESULTS);
        assertTrue(capabilities.supports(Capability.TRANSACTIONS));
        assertFalse(capabilities.supports(Capability.EDITABLE_RESULTS));
    }

    @Test
    void exposedSetCannotBeModified() {
        var capabilities = new Capabilities(Set.of(Capability.SCHEMAS));
        assertThrows(UnsupportedOperationException.class,
                () -> capabilities.supported().add(Capability.EDITABLE_RESULTS));
        assertTrue(capabilities.supports(Capability.SCHEMAS));
    }

    @Test
    void rejectsNullDeclarationsAndEntries() {
        assertThrows(NullPointerException.class, () -> new Capabilities(null));
        var source = new HashSet<Capability>();
        source.add(null);
        assertThrows(NullPointerException.class, () -> new Capabilities(source));
    }

    @Test
    void rejectsNullSupportChecks() {
        assertThrows(NullPointerException.class, () -> Capabilities.none().supports(null));
    }
}
