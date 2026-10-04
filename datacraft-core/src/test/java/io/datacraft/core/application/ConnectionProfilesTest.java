/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.application;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.datacraft.core.connection.*;
import io.datacraft.core.persistence.*;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionProfilesTest {
    private static final class Repository implements ConnectionRepository {
        List<SavedConnection> values = List.of(); boolean failSave, failLoad;
        @Override public List<SavedConnection> load() throws IOException {
            if (failLoad) throw new IOException("Unreadable profiles"); return values;
        }
        @Override public void save(List<SavedConnection> next) throws IOException {
            if (failSave) throw new IOException("Write failed"); values = List.copyOf(next);
        }
    }
    private static final class Credentials implements CredentialStore {
        final Map<UUID, char[]> entries = new HashMap<>(); boolean enabled = true; int reads, writes;
        @Override public boolean available() { return enabled; }
        @Override public Optional<char[]> read(UUID id) { reads++; return Optional.ofNullable(entries.get(id)).map(char[]::clone); }
        @Override public void write(UUID id, char[] value) { writes++; var old = entries.put(id, value.clone()); if (old != null) Arrays.fill(old, '\0'); }
        @Override public void delete(UUID id) { var old = entries.remove(id); if (old != null) Arrays.fill(old, '\0'); }
    }
    private final Repository repository = new Repository();
    private final Credentials credentials = new Credentials();
    private final ConnectionProfiles service = new ConnectionProfiles(repository, credentials);
    private SavedConnection profile(UUID id, boolean store) {
        return new SavedConnection(id, "Local", new ConnectionSettings("localhost", 5432, "test", "reader", Environment.TEST, TlsMode.VERIFY_FULL, 10), store);
    }
    private char[] generated() { return UUID.randomUUID().toString().toCharArray(); }
    private boolean wiped(char[] value) { for (char c : value) if (c != '\0') return false; return true; }
    @Test void defaultPreferenceSavesOnlyMetadataAndWipesCallerPassword() throws Exception {
        var profile = profile(UUID.randomUUID(), false); char[] password = generated();
        service.save(profile, password); assertTrue(wiped(password));
        assertEquals(List.of(profile), service.list()); assertEquals(0, credentials.writes);
        assertEquals(0, service.password(profile.id()).length); assertEquals(0, credentials.reads);
    }
    @Test void explicitConsentStoresRetrievesAndRevocationDeletes() throws Exception {
        var profile = profile(UUID.randomUUID(), true); char[] password = generated(); char[] expected = password.clone();
        try {
            service.save(profile, password); assertTrue(wiped(password)); assertEquals(1, credentials.writes);
            char[] returned = service.password(profile.id());
            try { assertTrue(Arrays.equals(expected, returned), "Stored credential mismatch"); }
            finally { Arrays.fill(returned, '\0'); }
            service.save(profile(profile.id(), false), new char[0]);
            assertFalse(credentials.entries.containsKey(profile.id())); assertEquals(0, service.password(profile.id()).length);
        } finally { credentials.delete(profile.id()); Arrays.fill(expected, '\0'); }
    }
    @Test void deletionRemovesProfileAndSecretAndIsIdempotent() throws Exception {
        var profile = profile(UUID.randomUUID(), true); service.save(profile, generated());
        service.delete(profile.id()); service.delete(profile.id());
        assertTrue(service.list().isEmpty()); assertTrue(credentials.entries.isEmpty());
    }
    @Test void failedMetadataWriteRestoresPreviousSecretAndCleansNewEntries() throws Exception {
        var profile = profile(UUID.randomUUID(), true); char[] expected = generated();
        try {
            service.save(profile, expected.clone()); repository.failSave = true;
            char[] replacement = generated(); assertThrows(IOException.class, () -> service.save(profile, replacement));
            assertTrue(wiped(replacement)); assertTrue(Arrays.equals(expected, credentials.entries.get(profile.id())), "Rollback mismatch");
            var fresh = profile(UUID.randomUUID(), true);
            assertThrows(IOException.class, () -> service.save(fresh, generated()));
            assertFalse(credentials.entries.containsKey(fresh.id()));
        } finally { credentials.delete(profile.id()); Arrays.fill(expected, '\0'); }
    }
    @Test void unreadableProfilesFailBeforeAnySecretWrite() {
        repository.failLoad = true; char[] password = generated();
        assertThrows(IOException.class, () -> service.save(profile(UUID.randomUUID(), true), password));
        assertTrue(wiped(password)); assertEquals(0, credentials.writes);
    }
    @Test void unavailableStoreNeverFallsBackToFilesOrReadsSecrets() throws Exception {
        credentials.enabled = false; var profile = profile(UUID.randomUUID(), true); char[] password = generated();
        assertFalse(service.canStorePassword()); assertThrows(IOException.class, () -> service.save(profile, password));
        assertTrue(wiped(password)); assertTrue(repository.values.isEmpty()); assertEquals(0, credentials.writes);
        repository.values = List.of(profile);
        assertEquals(0, service.password(profile.id()).length); assertEquals(0, credentials.reads);
        assertThrows(IOException.class, () -> service.delete(profile.id())); assertEquals(List.of(profile), service.list());
    }
    @Test void editedEndpointCannotReuseAnUnenteredStoredPassword() throws Exception {
        var profile = profile(UUID.randomUUID(), true); service.save(profile, generated());
        try {
            var changed = new SavedConnection(profile.id(), "Changed", new ConnectionSettings("other-host", 5432, "test", "reader", Environment.TEST, TlsMode.VERIFY_FULL, 10), true);
            assertThrows(IOException.class, () -> service.save(changed, new char[0]));
            assertThrows(IOException.class, () -> service.password(profile.id(), changed.settings()));
            assertEquals(List.of(profile), service.list());
            service.save(new SavedConnection(profile.id(), "Renamed", profile.settings(), true), new char[0]);
            assertEquals("Renamed", service.list().getFirst().name());
        } finally { credentials.delete(profile.id()); }
    }
    @Test void validatesNamesAndRejectsPasswordPreferenceForSqlite() {
        var settings = profile(UUID.randomUUID(), false).settings();
        assertThrows(IllegalArgumentException.class, () -> new SavedConnection(UUID.randomUUID(), " ", settings, false));
        assertThrows(IllegalArgumentException.class, () -> new SavedConnection(UUID.randomUUID(), "bad\nname", settings, false));
        var sqlite = new SqliteConnectionSettings(java.nio.file.Path.of("example.sqlite"), Environment.LOCAL, 10);
        assertThrows(IllegalArgumentException.class, () -> new SavedConnection(UUID.randomUUID(), "SQLite", sqlite, true));
    }
}
