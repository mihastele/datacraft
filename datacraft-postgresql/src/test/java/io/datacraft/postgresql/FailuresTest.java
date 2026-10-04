/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.postgresql;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.DatabaseException.Kind;
import io.datacraft.core.adapter.Capability;
import static org.junit.jupiter.api.Assertions.*;

class FailuresTest {
    @Test void removesDriverMessagesCausesAndSuppressedDiagnostics() {
        var failure = new SQLException("sensitive driver diagnostic", "28000",
                new IllegalStateException("sensitive cause"));
        failure.addSuppressed(new IllegalStateException("sensitive secondary diagnostic"));
        var sanitized = Failures.sanitize(failure);
        assertEquals(Kind.AUTHENTICATION, sanitized.kind());
        assertFalse(sanitized.toString().contains("sensitive"));
        assertNull(sanitized.getCause());
        assertEquals(0, sanitized.getSuppressed().length);
    }
    @Test void mapsUsefulFailureCategoriesWithoutDriverTypes() {
        assertEquals(Kind.CONNECTION, Failures.sanitize(new SQLException("", "08006")).kind());
        assertEquals(Kind.TIMEOUT, Failures.sanitize(new SQLTimeoutException()).kind());
        assertEquals(Kind.TIMEOUT, Failures.sanitize(new SQLException("", "57014")).kind());
        assertEquals(Kind.PERMISSION, Failures.sanitize(new SQLException("", "42501")).kind());
        assertEquals(Kind.NOT_FOUND, Failures.sanitize(new SQLException("", "3D000")).kind());
        assertEquals(Kind.OTHER, Failures.sanitize(new SQLException()).kind());
    }
    @Test void doesNotAdvertiseUnimplementedExecutionFeatures() {
        var capabilities = new PostgreSqlAdapter().capabilities();
        assertTrue(capabilities.supports(Capability.SCHEMAS));
        assertTrue(capabilities.supports(Capability.MATERIALIZED_VIEWS));
        assertFalse(capabilities.supports(Capability.TRANSACTIONS));
        assertFalse(capabilities.supports(Capability.EDITABLE_RESULTS));
        assertFalse(capabilities.supports(Capability.EXPLAIN_PLANS));
    }
}
