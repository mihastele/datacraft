/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import io.datacraft.core.connection.*;
import io.datacraft.core.persistence.ConnectionRepository;

/** Version 1 UTF-8 properties containing only named connection settings, never secrets. */
public final class FileConnectionRepository implements ConnectionRepository {
    private static final int MAX_BYTES = 1048576, MAX_PROFILES = 200;
    private final Path file;
    private byte[] expected;
    private boolean locked;
    public FileConnectionRepository(Path directory) { file = directory.toAbsolutePath().normalize().resolve("connections.properties"); }
    private byte[] bytes() throws IOException {
        if (!Files.exists(file)) return new byte[0];
        if (Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES) throw invalid();
        try (var input = Files.newInputStream(file)) {
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw invalid();
            return data;
        }
    }
    @Override public synchronized List<SavedConnection> load() throws IOException {
        byte[] data = bytes();
        var properties = new Properties();
        if (data.length == 0) {
            if (Files.exists(file)) throw invalid();
            expected = data; return List.of();
        }
        try {
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
            properties.load(new StringReader(text));
            if (!"1".equals(required(properties, "version"))) throw invalid();
            int count = Integer.parseInt(required(properties, "count"));
            if (count < 0 || count > MAX_PROFILES) throw invalid();
            var profiles = new ArrayList<SavedConnection>(); var ids = new HashSet<UUID>();
            var keys = new HashSet<>(List.of("version", "count"));
            for (int i = 0; i < count; i++) {
                String prefix = "profile." + i + ".";
                for (String key : List.of("id", "name", "kind", "environment", "timeout", "storePassword")) keys.add(prefix + key);
                var id = UUID.fromString(required(properties, prefix + "id"));
                if (!ids.add(id)) throw invalid();
                var kind = DatabaseKind.valueOf(required(properties, prefix + "kind"));
                var environment = Environment.valueOf(required(properties, prefix + "environment"));
                int timeout = Integer.parseInt(required(properties, prefix + "timeout"));
                String remember = required(properties, prefix + "storePassword");
                if (!List.of("true", "false").contains(remember)) throw invalid();
                ConnectionProfile settings;
                if (kind == DatabaseKind.SQLITE) {
                    keys.add(prefix + "file");
                    settings = new SqliteConnectionSettings(Path.of(required(properties, prefix + "file")), environment, timeout);
                } else {
                    for (String key : List.of("host", "port", "database", "username", "tls")) keys.add(prefix + key);
                    settings = new ConnectionSettings(kind, required(properties, prefix + "host"),
                        Integer.parseInt(required(properties, prefix + "port")), required(properties, prefix + "database"),
                        required(properties, prefix + "username"), environment,
                        TlsMode.valueOf(required(properties, prefix + "tls")), timeout);
                }
                profiles.add(new SavedConnection(id, required(properties, prefix + "name"), settings, Boolean.parseBoolean(remember)));
            }
            if (!keys.equals(properties.stringPropertyNames())) throw invalid();
            expected = data; return List.copyOf(profiles);
        } catch (IllegalArgumentException failure) { throw invalid(); }
    }
    @Override public synchronized void save(List<SavedConnection> profiles) throws IOException {
        if (expected == null) load();
        if (profiles.size() > MAX_PROFILES || profiles.stream().map(SavedConnection::id).distinct().count() != profiles.size()) throw invalid();
        var properties = new Properties(); properties.setProperty("version", "1"); properties.setProperty("count", Integer.toString(profiles.size()));
        for (int i = 0; i < profiles.size(); i++) {
            var profile = profiles.get(i); var settings = profile.settings(); String prefix = "profile." + i + ".";
            put(properties, prefix, "id", profile.id()); put(properties, prefix, "name", profile.name());
            put(properties, prefix, "kind", settings.kind().name()); put(properties, prefix, "environment", settings.environment().name());
            put(properties, prefix, "timeout", settings.timeoutSeconds()); put(properties, prefix, "storePassword", profile.storePassword());
            if (settings instanceof SqliteConnectionSettings sqlite) put(properties, prefix, "file", sqlite.file());
            else if (settings instanceof ConnectionSettings server) {
                put(properties, prefix, "host", server.host()); put(properties, prefix, "port", server.port());
                put(properties, prefix, "database", server.database()); put(properties, prefix, "username", server.username());
                put(properties, prefix, "tls", server.tlsMode().name());
            }
        }
        var output = new StringWriter(); properties.store(output, "DataCraft connection profiles — no passwords");
        byte[] data = output.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw invalid();
        withLock(() -> {
            if (!Arrays.equals(expected, bytes())) throw new IOException("Profiles changed in another window. Reload before saving.");
            Path temporary = Files.createTempFile(file.getParent(), ".connections-", ".tmp");
            try {
                secure(temporary, "rw-------");
                try (var stream = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    var buffer = java.nio.ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) stream.write(buffer);
                    stream.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                expected = data;
            } finally { Files.deleteIfExists(temporary); }
            return null;
        });
    }
    @Override public synchronized <T> T withLock(Operation<T> operation) throws IOException {
        if (locked) return operation.run();
        Files.createDirectories(file.getParent()); secure(file.getParent(), "rwx------");
        try (var channel = FileChannel.open(file.resolveSibling("connections.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not lock the connection profiles.");
            locked = true;
            try { return operation.run(); } finally { locked = false; }
        } catch (java.nio.channels.OverlappingFileLockException failure) {
            throw new IOException("Another window is updating connection profiles.");
        }
    }

    private static void secure(Path path, String mode) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }
    private static void put(Properties properties, String prefix, String key, Object value) { properties.setProperty(prefix + key, value.toString()); }
    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key); if (value == null || value.length() > 8192) throw invalid(); return value;
    }
    private static IOException invalid() { return new IOException("Connection profiles are invalid or use an unsupported format. The file was not changed."); }
}
