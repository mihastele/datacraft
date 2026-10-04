/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.datacraft.core.connection.*;
import static org.junit.jupiter.api.Assertions.*;

class FileConnectionRepositoryTest {
    @TempDir Path directory;
    private SavedConnection profile(DatabaseKind kind) {
        ConnectionProfile settings = kind == DatabaseKind.SQLITE
            ? new SqliteConnectionSettings(directory.resolve("db ü.sqlite"), Environment.TEST, 10)
            : new ConnectionSettings(kind, "localhost", 5432, "db=ü", "reader", Environment.TEST, TlsMode.VERIFY_FULL, 10);
        return new SavedConnection(UUID.randomUUID(), "Local ü " + kind, settings, kind != DatabaseKind.SQLITE);
    }
    @Test void persistsEveryKindAndUpdatesDeletesWithoutCredentialFields() throws Exception {
        var repository = new FileConnectionRepository(directory);
        var profiles = Arrays.stream(DatabaseKind.values()).map(this::profile).toList();
        assertEquals(List.of(), repository.load()); repository.save(profiles);
        assertEquals(profiles, new FileConnectionRepository(directory).load());
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(directory.resolve("connections.properties"))) { properties.load(reader); }
        assertFalse(properties.stringPropertyNames().stream().anyMatch(k -> k.endsWith(".password") || k.endsWith(".secret")));
        repository.save(profiles.subList(0, 1));
        assertEquals(1, repository.load().size()); repository.save(List.of());
        assertTrue(repository.load().isEmpty());
        try (var children = Files.list(directory)) { assertFalse(children.anyMatch(p -> p.toString().endsWith(".tmp"))); }
    }
    @Test void rejectsCorruptionNewerFormatsAndInjectedSecretKeysWithoutOverwriting() throws Exception {
        Path file = directory.resolve("connections.properties");
        for (String malformed : List.of("version=2\ncount=0\n", "version=1\ncount=-1\n", "version=1\ncount=0\npassword=forbidden\n")) {
            Files.writeString(file, malformed);
            var repository = new FileConnectionRepository(directory);
            assertThrows(IOException.class, repository::load);
            assertThrows(IOException.class, () -> repository.save(List.of(profile(DatabaseKind.POSTGRESQL))));
            assertTrue(malformed.equals(Files.readString(file)), "Invalid file must be preserved");
        }
    }
    @Test void detectsConcurrentChangesInsteadOfLosingAnotherWindowsProfiles() throws Exception {
        var first = new FileConnectionRepository(directory); var second = new FileConnectionRepository(directory);
        first.load(); second.load();
        var profile = profile(DatabaseKind.SQLITE); first.save(List.of(profile));
        assertThrows(IOException.class, () -> second.save(List.of()));
        assertEquals(List.of(profile), second.load());
    }
    @Test void rejectsOversizedFiles() throws Exception {
        Files.write(directory.resolve("connections.properties"), new byte[1048577]);
        assertThrows(IOException.class, new FileConnectionRepository(directory)::load);
    }
    @Test void serializesTheWholeOperationAndPreservesEmptyOrInvalidUtf8Files() throws Exception {
        var first = new FileConnectionRepository(directory); var second = new FileConnectionRepository(directory);
        first.withLock(() -> {
            assertThrows(IOException.class, () -> second.withLock(() -> { fail("Concurrent mutation must not run"); return null; }));
            return null;
        });
        Path file = directory.resolve("connections.properties");
        Files.write(file, new byte[0]); assertThrows(IOException.class, first::load);
        Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28}); assertThrows(IOException.class, first::load);
    }
}
