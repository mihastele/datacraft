/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.*;
import io.datacraft.core.connection.*;
import static org.junit.jupiter.api.Assertions.*;

class MySqlPolicyTest {
    @Test void distinguishesDialectQuotesCommentsAndSubtraction() throws Exception {
        assertEquals("# header\nSELECT 'semi;colon' AS `a``b`", MySqlQueryPolicy.singleSelect("# header\nSELECT 'semi;colon' AS `a``b`; -- tail"));
        assertEquals("SELECT 1--2", MySqlQueryPolicy.singleSelect("SELECT 1--2"));
        assertEquals("SELECT 'a''b'", MySqlQueryPolicy.singleSelect("SELECT 'a''b'"));
        assertEquals("SELECT 'a\\'", MySqlQueryPolicy.singleSelect("SELECT 'a\\'"));
    }
    @Test void rejectsExecutableCommentsScriptsParametersAndInto() {
        for (String sql : List.of("DELETE FROM dc", "SELECT 1; SELECT 2", "SELECT /*!50000 1*/", "SELECT /*M! 1*/",
                "SELECT /*+ MAX_EXECUTION_TIME(10) */ 1", "SELECT 1 INTO OUTFILE 'x'", "SELECT @variable", "SELECT ?",
                "SELECT 1 /* outer /* nested */ */", "SELECT 'unterminated", "SELECT 1 /* unfinished")) {
            assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class, () -> MySqlQueryPolicy.singleSelect(sql), sql).kind());
        }
    }
    @Test void constructsFixedSafeTransportOptionsWithoutCredentialsOrDatabaseInterpolation() {
        var settings = new ConnectionSettings(DatabaseKind.MYSQL, "::1", 3306, "craft?sslMode=disable", "reader",
                Environment.TEST, TlsMode.VERIFY_FULL, 5);
        String url = MySqlMariaDbAdapter.connectionUrl(settings);
        assertTrue(url.startsWith("jdbc:mariadb://[::1]:3306/?sslMode=verify-full"));
        assertFalse(url.contains(settings.database())); assertFalse(url.contains(settings.username()));
        assertTrue(url.contains("allowMultiQueries=false")); assertTrue(url.contains("allowLocalInfile=false"));
        assertTrue(url.contains("allowPublicKeyRetrieval=false"));
    }
    @Test void rejectsOtherAdaptersProfilesAndExposesOnlyImplementedCapabilities() {
        var adapter = new MySqlMariaDbAdapter();
        assertThrows(DatabaseException.class, () -> adapter.connect(new SqliteConnectionSettings(Path.of("test.sqlite"), Environment.TEST, 5), new char[0]));
        assertThrows(DatabaseException.class, () -> adapter.connect(new ConnectionSettings("localhost", 5432, "test", "reader", Environment.TEST, TlsMode.DISABLED, 5), new char[0]));
        assertTrue(adapter.capabilities().supports(Capability.MULTIPLE_DATABASES));
        assertFalse(adapter.capabilities().supports(Capability.EDITABLE_RESULTS));
    }
    @Test void sanitizesWithoutRetainingDriverSqlPathsOrCauses() {
        assertEquals(DatabaseException.Kind.TIMEOUT, Failures.sanitize(new SQLTimeoutException("sensitive SQL")).kind());
        for (var entry : java.util.Map.of(1792, DatabaseException.Kind.POLICY, 1146, DatabaseException.Kind.NOT_FOUND,
                1044, DatabaseException.Kind.PERMISSION, 1317, DatabaseException.Kind.CANCELLED, 1969, DatabaseException.Kind.TIMEOUT).entrySet()) {
            var error = Failures.sanitize(new SQLException("sensitive SQL", "HY000", entry.getKey()));
            assertEquals(entry.getValue(), error.kind()); assertNull(error.getCause()); assertFalse(error.getMessage().contains("sensitive"));
        }
    }
}
