/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.datacraft.core.application.WorkspaceService;
import io.datacraft.core.connection.*;
import static org.junit.jupiter.api.Assertions.*;

class AdapterRegistryTest {
    private DatabaseAdapter adapter() {
        return new DatabaseAdapter() {
            @Override public Capabilities capabilities() { return Capabilities.none(); }
            @Override public DatabaseSession connect(ConnectionProfile profile, char[] password) throws DatabaseException {
                throw new DatabaseException(DatabaseException.Kind.CONNECTION);
            }
        };
    }
    @Test void resolvesExplicitStrategiesAndDefensivelyCopiesRegistration() throws Exception {
        var postgres = adapter(); var sqlite = adapter();
        var registrations = new EnumMap<DatabaseKind, DatabaseAdapter>(DatabaseKind.class);
        registrations.put(DatabaseKind.POSTGRESQL, postgres); registrations.put(DatabaseKind.SQLITE, sqlite);
        var registry = new AdapterRegistry(registrations); registrations.clear();
        assertSame(postgres, registry.resolve(DatabaseKind.POSTGRESQL));
        assertSame(sqlite, registry.resolve(DatabaseKind.SQLITE));
        assertEquals(2, registry.available().size());
        assertThrows(UnsupportedOperationException.class, () -> registry.available().clear());
        assertThrows(IllegalArgumentException.class, () -> new AdapterRegistry(Map.of()));
    }
    @Test void unsupportedDatabaseFailsBeforeConnectionAndStillWipesCredentials() {
        var registry = new AdapterRegistry(Map.of(DatabaseKind.POSTGRESQL, adapter()));
        var workspace = new WorkspaceService(registry); char[] password = {'x'};
        assertEquals(DatabaseException.Kind.POLICY, assertThrows(DatabaseException.class,
                () -> workspace.connect(new SqliteConnectionSettings(Path.of("test.sqlite"), Environment.TEST, 2), password)).kind());
        assertEquals('\0', password[0]); assertFalse(workspace.isConnected());
    }
    @Test void fileProfileKeepsTransportFieldsOutAndNormalizesLocation() {
        var profile = new SqliteConnectionSettings(Path.of("directory", "..", "test.sqlite"), Environment.LOCAL, 3);
        assertEquals(DatabaseKind.SQLITE, profile.kind());
        assertTrue(profile.file().isAbsolute()); assertEquals(Path.of("test.sqlite").toAbsolutePath(), profile.file());
        for (int timeout : List.of(0, 301)) assertThrows(IllegalArgumentException.class,
                () -> new SqliteConnectionSettings(Path.of("test.sqlite"), Environment.LOCAL, timeout));
    }
}
