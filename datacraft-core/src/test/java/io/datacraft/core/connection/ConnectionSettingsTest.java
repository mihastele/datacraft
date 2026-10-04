/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionSettingsTest {
    private ConnectionSettings settings(String host, int port, int timeout) {
        return new ConnectionSettings(host, port, "craft?quoted", "reader", Environment.TEST,
                TlsMode.VERIFY_FULL, timeout);
    }

    @Test void rejectsUrlsAndHostPropertyInjection() {
        for (var host : new String[] {"", "jdbc:postgresql://localhost", "localhost?sslmode=disable",
                "localhost/other", "localhost\nother"}) {
            assertThrows(IllegalArgumentException.class, () -> settings(host, 5432, 10));
        }
    }
    @Test void validatesPortAndTimeoutBounds() {
        assertThrows(IllegalArgumentException.class, () -> settings("localhost", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> settings("localhost", 65536, 10));
        assertThrows(IllegalArgumentException.class, () -> settings("localhost", 5432, 0));
        assertThrows(IllegalArgumentException.class, () -> settings("localhost", 5432, 301));
    }
    @Test void preservesStructuredSettingsAndIpv6() {
        var settings = settings("::1", 5432, 10);
        assertEquals("::1", settings.host());
        assertEquals("craft?quoted", settings.database());
        assertEquals(TlsMode.VERIFY_FULL, settings.tlsMode());
        assertEquals(Environment.TEST, settings.environment());
    }
    @Test void requiresExplicitEnvironmentAndTlsChoice() {
        assertThrows(NullPointerException.class, () -> new ConnectionSettings("localhost", 5432,
                "craft", "reader", null, TlsMode.VERIFY_FULL, 10));
        assertThrows(NullPointerException.class, () -> new ConnectionSettings("localhost", 5432,
                "craft", "reader", Environment.TEST, null, 10));
    }
    @Test void distinguishesServerKindsAndRequiresFileSettingsForSqlite() {
        assertEquals(DatabaseKind.POSTGRESQL, settings("localhost", 5432, 5).kind());
        for (var kind : new DatabaseKind[] {DatabaseKind.MYSQL, DatabaseKind.MARIADB}) {
            assertEquals(kind, new ConnectionSettings(kind, "localhost", 3306, "craft", "reader",
                    Environment.TEST, TlsMode.VERIFY_FULL, 5).kind());
        }
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings(DatabaseKind.SQLITE,
                "localhost", 3306, "craft", "reader", Environment.TEST, TlsMode.DISABLED, 5));
    }
}
