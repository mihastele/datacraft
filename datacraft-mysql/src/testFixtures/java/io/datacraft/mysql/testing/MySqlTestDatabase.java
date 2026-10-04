/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql.testing;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import io.datacraft.core.connection.*;

/** Test-only provisioning shared by adapter/UI tests. Owns only its newly created container. */
public final class MySqlTestDatabase implements AutoCloseable {
    private final DatabaseKind kind;
    private final String password = UUID.randomUUID().toString();
    private final String rootPassword = UUID.randomUUID().toString();
    private final GenericContainer<?> container;
    public MySqlTestDatabase(DatabaseKind kind) {
        this(kind, true);
    }
    public MySqlTestDatabase(DatabaseKind kind, boolean tlsEnabled) {
        if (kind != DatabaseKind.MYSQL && kind != DatabaseKind.MARIADB) throw new IllegalArgumentException("Unsupported fixture kind.");
        this.kind = kind;
        String image = kind == DatabaseKind.MYSQL
                ? "mysql@sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242"
                : "mariadb@sha256:1292844148b311e4ed4300022a996d39083f415a963e970cf47cad1b3b18e3a6";
        String prefix = kind == DatabaseKind.MYSQL ? "MYSQL" : "MARIADB";
        container = new GenericContainer<>(DockerImageName.parse(image)).withExposedPorts(3306)
                .withEnv(prefix + "_DATABASE", database()).withEnv(prefix + "_USER", username())
                .withEnv(prefix + "_PASSWORD", password).withEnv(prefix + "_ROOT_PASSWORD", rootPassword)
                .withEnv(prefix + "_ROOT_HOST", "%")
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(120));
        var arguments = new java.util.ArrayList<String>();
        if (kind == DatabaseKind.MYSQL) arguments.add("--mysql-native-password=ON");
        arguments.add("--log-bin-trust-function-creators=ON");
        if (!tlsEnabled) arguments.add("--ssl=OFF");
        container.withCommand(arguments.toArray(String[]::new));
    }
    public void start() throws Exception {
        try {
            container.start();
            try (var connection = admin(); var statement = connection.createStatement()) {
                if (kind == DatabaseKind.MYSQL) {
                    // Only this disposable plaintext fixture uses legacy auth; product defaults remain verified TLS.
                    try (var alter = connection.prepareStatement("ALTER USER 'dc_reader'@'%' IDENTIFIED WITH mysql_native_password BY ?")) {
                        alter.setString(1, password); alter.execute();
                    }
                }
                statement.execute("USE datacraft_test");
                try (var fixture = getClass().getResourceAsStream("/migrations/001_mysql_fixture.sql")) {
                    if (fixture == null) throw new IllegalStateException("Missing fixture migration.");
                    String sql = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
                    for (String migration : sql.split(";")) if (!migration.isBlank()) statement.execute(migration);
                }
                try (var fixture = getClass().getResourceAsStream("/migrations/002_write_function.sql")) {
                    if (fixture == null) throw new IllegalStateException("Missing function migration.");
                    statement.execute(new String(fixture.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception failure) {
            container.stop();
            // Provisioning statements contain runtime authentication data; do not retain raw driver causes.
            throw new IllegalStateException("Disposable " + kind + " fixture provisioning failed.");
        }
    }
    public Connection admin() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        do {
            try {
                // Retrieval is test-admin-only, on an owned random-port container, never a product option.
                return DriverManager.getConnection("jdbc:mariadb://" + host() + ":" + port()
                        + "/?sslMode=disable&allowPublicKeyRetrieval=true&connectTimeout=2000&socketTimeout=5000", "root", rootPassword);
            } catch (SQLException failure) { Thread.sleep(100); }
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Disposable database readiness failed.");
    }
    public ConnectionSettings settings(TlsMode tls) {
        return new ConnectionSettings(kind, host(), port(), database(), username(), Environment.TEST, tls, 5);
    }
    public String host() { return container.getHost(); }
    public int port() { return container.getMappedPort(3306); }
    public String database() { return "datacraft_test"; }
    public String username() { return "dc_reader"; }
    public char[] password() { return password.toCharArray(); }
    public DatabaseKind kind() { return kind; }
    @Override public void close() { container.stop(); }
}
