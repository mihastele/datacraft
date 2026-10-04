/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.datacraft.mysql.MySqlMariaDbAdapter;
import io.datacraft.mysql.testing.MySqlTestDatabase;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.datacraft.core.application.WorkspaceService;
import io.datacraft.core.connection.*;
import io.datacraft.core.query.QueryCell;
import io.datacraft.postgresql.PostgreSqlAdapter;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesktopWorkflowTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f")
            .withDatabaseName("datacraft_ui_test").withUsername("datacraft_ui_test")
            .withPassword(UUID.randomUUID().toString());
    private static Stage stage;
    private static DesktopWindow window;

    @BeforeAll static void setup() throws Exception {
        try {
            POSTGRES.start();
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var fixture = DesktopWorkflowTest.class.getResourceAsStream("/migrations/001_desktop_workflow.sql")) {
                assertNotNull(fixture);
                statement.execute(new String(fixture.readAllBytes(), StandardCharsets.UTF_8));
            }
            var started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            assertTrue(started.await(10, TimeUnit.SECONDS));
            fx(() -> {
                Platform.setImplicitExit(false);
                window = new DesktopWindow(new DesktopController(new WorkspaceService(new io.datacraft.core.adapter.AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, new PostgreSqlAdapter(), DatabaseKind.SQLITE, new io.datacraft.sqlite.SqliteAdapter(), DatabaseKind.MYSQL, new MySqlMariaDbAdapter(), DatabaseKind.MARIADB, new MySqlMariaDbAdapter())))));
                stage = new Stage(); stage.setTitle("DataCraft — automated workflow verification");
                stage.setScene(new Scene(window.root(), 1240, 820)); stage.show();
                return null;
            });
        } catch (Exception failure) { POSTGRES.stop(); throw failure; }
    }
    @AfterAll static void cleanup() throws Exception {
        try {
            if (window != null) fx(window::shutdown).get(15, TimeUnit.SECONDS);
            if (stage != null) fx(() -> { stage.hide(); return null; });
        } finally { Platform.exit(); POSTGRES.stop(); }
    }
    private static <T> T fx(Callable<T> call) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { future.complete(call.call()); } catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        return future.get(15, TimeUnit.SECONDS);
    }
    private static boolean isShowing(javafx.scene.Node node) {
        for (var ancestor = node; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) return false;
        }
        return node.getScene() != null && node.getScene().getWindow().isShowing();
    }
    private static <T> T node(String id, Class<T> type) {
        if (List.of("editor", "run", "cancel", "results", "row-limit", "query-timeout").contains(id)) {
            return type.cast(window.activeQuery().tab.getContent().lookup("#" + id));
        }
        return type.cast(window.root().lookup("#" + id));
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(25);
        }
        fail("Desktop workflow did not reach the expected state.");
    }
    @SuppressWarnings("unchecked")
    private static TreeView<DesktopWindow.ExplorerNode> explorer() { return node("explorer", TreeView.class); }
    private static QueryCell cell(int row, int column) {
        var values = (List<?>) node("results", TableView.class).getItems().get(row);
        return (QueryCell) values.get(column);
    }

    @Test @Order(1) @SuppressWarnings("unchecked") void connectsExploresQueriesCancelsReusesAndDisconnects() throws Exception {
        fx(() -> {
            snapshot("disconnected-workspace.png");
            assertTrue(node("run", Button.class).isDisabled());
            node("host", TextField.class).setText(POSTGRES.getHost());
            node("port", TextField.class).setText(Integer.toString(POSTGRES.getMappedPort(5432)));
            node("database", TextField.class).setText(POSTGRES.getDatabaseName());
            node("username", TextField.class).setText(POSTGRES.getUsername());
            node("password", PasswordField.class).setText(POSTGRES.getPassword());
            ((ComboBox<TlsMode>) node("tls", ComboBox.class)).setValue(TlsMode.DISABLED);
            ((ComboBox<Environment>) node("environment", ComboBox.class)).setValue(Environment.TEST);
            node("connect", Button.class).fire(); return null;
        });
        await(() -> node("status", Label.class).getText().startsWith("Connected."));
        fx(() -> {
            assertEquals("", node("password", PasswordField.class).getText());
            assertTrue(node("environment-badge", Label.class).getText().contains("TEST"));
            explorer().getRoot().getChildren().stream().filter(item -> "mvp_test".equals(item.getValue().schema()))
                    .findFirst().orElseThrow().setExpanded(true);
            return null;
        });
        await(() -> node("status", Label.class).getText().contains("relations in mvp_test"));
        fx(() -> {
            var schema = explorer().getRoot().getChildren().stream().filter(item -> "mvp_test".equals(item.getValue().schema())).findFirst().orElseThrow();
            var relation = schema.getChildren().stream().filter(item -> item.getValue().relation() != null
                    && "widgets".equals(item.getValue().relation().name())).findFirst().orElseThrow();
            explorer().getSelectionModel().select(relation); return null;
        });
        await(() -> node("columns", TableView.class).getItems().size() == 2);
        verifySqlIntelligence("mvp_test.widgets", "sql-intelligence.png");
        fx(() -> {
            node("editor", TextArea.class).setText("SELECT id, label FROM mvp_test.widgets ORDER BY id");
            node("run", Button.class).fire(); return null;
        });
        await(() -> node("results", TableView.class).getItems().size() == 2);
        fx(() -> {
            assertEquals("alpha", cell(0, 1).value());
            assertTrue(cell(1, 1).isNull());
            snapshot("workspace.png");
            node("editor", TextArea.class).setText("SELECT pg_sleep(30)");
            node("run", Button.class).fire(); return null;
        });
        // Verify the UI thread remains available while the actual backend query runs.
        boolean running = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            while (!running && System.nanoTime() < deadline) {
                try (var statement = admin.createStatement(); var rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_stat_activity WHERE state = 'active'
                        AND pid <> pg_backend_pid() AND query LIKE '%pg_sleep(30)%'
                        """)) { rows.next(); running = rows.getInt(1) > 0; }
                if (!running) Thread.sleep(25);
            }
        }
        assertTrue(running);
        fx(() -> { assertFalse(node("cancel", Button.class).isDisabled()); node("cancel", Button.class).fire(); return null; });
        await(() -> node("status", Label.class).getText().startsWith("Query cancelled."));
        fx(() -> {
            var editor = node("editor", TextArea.class); editor.setText("SELECT 42 AS answer");
            editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, true, false, false));
            return null;
        });
        await(() -> node("results", TableView.class).getItems().size() == 1 && "42".equals(cell(0, 0).value()));
        fx(() -> { node("disconnect", Button.class).fire(); return null; });
        await(() -> node("status", Label.class).getText().startsWith("Disconnected."));
        fx(() -> {
            assertTrue(node("run", Button.class).isDisabled());
            assertNull(explorer().getRoot());
            assertEquals("", node("password", PasswordField.class).getText());
            return null;
        });
        try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = admin.createStatement(); var rows = statement.executeQuery("""
                        SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()
                        AND pid <> pg_backend_pid() AND backend_type = 'client backend'
                        """)) { rows.next(); assertEquals(0, rows.getInt(1)); }
    }

    private static void snapshot(String filename) throws Exception {
        var image = window.root().snapshot(null, null);
        var buffer = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++) {
            buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        var directory = Path.of("build", "desktop-screenshots"); Files.createDirectories(directory);
        assertTrue(ImageIO.write(buffer, "png", directory.resolve(filename).toFile()));
    }

    @Test @Order(2) @SuppressWarnings("unchecked") void switchesToSqliteAndRunsActualFileWorkflow(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("desktop.sqlite");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement();
                var fixture = getClass().getResourceAsStream("/migrations/002_sqlite_desktop_workflow.sql")) {
            assertNotNull(fixture);
            for (String sql : new String(fixture.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
        fx(() -> {
            ((ComboBox<DatabaseKind>) node("database-kind", ComboBox.class)).setValue(DatabaseKind.SQLITE);
            assertFalse(isShowing(node("host", TextField.class)));
            assertTrue(isShowing(node("sqlite-file", TextField.class)));
            node("sqlite-file", TextField.class).setText(file.toString());
            node("connect", Button.class).fire(); return null;
        });
        await(() -> node("status", Label.class).getText().startsWith("Connected."));
        fx(() -> {
            assertTrue(node("database-kind", ComboBox.class).isDisabled());
            assertEquals("main", explorer().getRoot().getChildren().getFirst().getValue().schema());
            explorer().getRoot().getChildren().getFirst().setExpanded(true); return null;
        });
        await(() -> node("status", Label.class).getText().contains("relations in main"));
        fx(() -> {
            var relation = explorer().getRoot().getChildren().getFirst().getChildren().getFirst();
            assertEquals("widgets", relation.getValue().relation().name());
            explorer().getSelectionModel().select(relation); return null;
        });
        await(() -> node("columns", TableView.class).getItems().size() == 2);
        verifySqlIntelligence("main.widgets", null);
        fx(() -> {
            node("editor", TextArea.class).setText("SELECT id, label FROM widgets ORDER BY id");
            node("run", Button.class).fire(); return null;
        });
        await(() -> node("results", TableView.class).getItems().size() == 2);
        fx(() -> {
            assertEquals("local SQLite", cell(0, 1).value()); assertTrue(cell(1, 1).isNull());
            snapshot("sqlite-workspace.png");
            node("editor", TextArea.class).setText("DELETE FROM widgets"); node("run", Button.class).fire(); return null;
        });
        await(() -> node("status", Label.class).getText().startsWith("Blocked:"));
        fx(() -> {
            node("editor", TextArea.class).setText("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000000000) SELECT sum(x) FROM n");
            node("run", Button.class).fire(); return null;
        });
        await(() -> !node("cancel", Button.class).isDisabled()); Thread.sleep(150);
        fx(() -> { node("cancel", Button.class).fire(); return null; });
        await(() -> node("status", Label.class).getText().startsWith("Query cancelled."));
        fx(() -> { node("editor", TextArea.class).setText("SELECT count(*) FROM widgets"); node("run", Button.class).fire(); return null; });
        await(() -> node("results", TableView.class).getItems().size() == 1 && "2".equals(cell(0, 0).value()));
        fx(() -> { node("disconnect", Button.class).fire(); return null; });
        await(() -> node("status", Label.class).getText().startsWith("Disconnected."));
        fx(() -> { assertFalse(node("database-kind", ComboBox.class).isDisabled()); return null; });
        Files.delete(file); assertFalse(Files.exists(file));
    }

    @ParameterizedTest @Order(3) @EnumSource(value=DatabaseKind.class, names={"MYSQL","MARIADB"})
    @SuppressWarnings("unchecked") void mysqlAndMariaDbDesktopWorkflows(DatabaseKind kind) throws Exception {
        try (var database = new MySqlTestDatabase(kind)) {
            database.start();
            fx(() -> {
                ((ComboBox<DatabaseKind>) node("database-kind", ComboBox.class)).setValue(kind);
                assertTrue(isShowing(node("host", TextField.class)));
                assertFalse(isShowing(node("sqlite-file", TextField.class)));
                node("host", TextField.class).setText(database.host());
                node("port", TextField.class).setText(Integer.toString(database.port()));
                node("database", TextField.class).setText(database.database());
                node("username", TextField.class).setText(database.username());
                char[] password = database.password();
                try { node("password", PasswordField.class).setText(new String(password)); }
                finally { java.util.Arrays.fill(password, '\0'); }
                ((ComboBox<TlsMode>) node("tls", ComboBox.class)).setValue(TlsMode.DISABLED);
                node("connect", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Connected."));
            fx(() -> {
                assertEquals("", node("password", PasswordField.class).getText());
                assertTrue(node("database-kind", ComboBox.class).isDisabled());
                explorer().getRoot().getChildren().stream().filter(item -> database.database().equals(item.getValue().schema()))
                        .findFirst().orElseThrow().setExpanded(true); return null;
            });
            await(() -> node("status", Label.class).getText().contains("relations in " + database.database()));
            fx(() -> {
                var schema = explorer().getRoot().getChildren().stream().filter(item -> database.database().equals(item.getValue().schema())).findFirst().orElseThrow();
                var relation = schema.getChildren().stream().filter(item -> item.getValue().relation() != null
                        && "Odd' Table".equals(item.getValue().relation().name())).findFirst().orElseThrow();
                explorer().getSelectionModel().select(relation); return null;
            });
            await(() -> node("columns", TableView.class).getItems().size() == 4);
            verifySqlIntelligence("`" + database.database() + "`.`Odd' Table`", null);
            fx(() -> {
                node("editor", TextArea.class).setText("SELECT id, `Camel Column` FROM `Odd' Table` ORDER BY id");
                node("run", Button.class).fire(); return null;
            });
            await(() -> node("results", TableView.class).getItems().size() == 4);
            fx(() -> {
                assertEquals("alpha", cell(0, 1).value()); assertTrue(cell(1, 1).isNull());
                snapshot(kind.name().toLowerCase(java.util.Locale.ROOT) + "-workspace.png");
                node("editor", TextArea.class).setText("DELETE FROM dc"); node("run", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Blocked:"));
            fx(() -> { node("editor", TextArea.class).setText("SELECT SLEEP(30)"); node("run", Button.class).fire(); return null; });
            boolean running = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try (var admin = database.admin()) {
                while (!running && System.nanoTime() < deadline) {
                    try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM information_schema.PROCESSLIST WHERE USER='dc_reader' AND COMMAND='Query' AND INFO LIKE '%SLEEP(30)%'")) {
                        rows.next(); running = rows.getInt(1) > 0;
                    }
                    if (!running) Thread.sleep(25);
                }
            }
            assertTrue(running);
            fx(() -> { node("cancel", Button.class).fire(); return null; });
            await(() -> node("status", Label.class).getText().startsWith("Query cancelled."));
            fx(() -> { node("editor", TextArea.class).setText("SELECT 42 AS answer"); node("run", Button.class).fire(); return null; });
            await(() -> node("results", TableView.class).getItems().size() == 1 && "42".equals(cell(0, 0).value()));
            fx(() -> { node("disconnect", Button.class).fire(); return null; });
            await(() -> node("status", Label.class).getText().startsWith("Disconnected."));
            if (kind == DatabaseKind.MARIADB) {
                fx(() -> {
                    stage.setMaximized(false); stage.setIconified(false); stage.setResizable(true);
                    stage.setWidth(980); stage.setHeight(650); return null;
                });
                try { await(() -> stage.getScene().getWidth() < 1000 && stage.getScene().getHeight() < 650); }
                catch (AssertionError failure) {
                    String geometry = fx(() -> "Window " + stage.getWidth() + "x" + stage.getHeight()
                            + "; scene " + stage.getScene().getWidth() + "x" + stage.getScene().getHeight());
                    fail("Minimum-window resize failed: " + geometry, failure);
                }
                fx(() -> { snapshot("minimum-workspace.png"); return null; });
                fx(() -> { stage.setWidth(1256); stage.setHeight(859); return null; });
            }

            try (var admin = database.admin(); var statement = admin.createStatement();
                    var rows = statement.executeQuery("SELECT count(*) FROM information_schema.PROCESSLIST WHERE USER='dc_reader'")) {
                rows.next(); assertEquals(0, rows.getInt(1));
            }
        }
    }
    @Test @Order(4) @SuppressWarnings("unchecked")
    void savedProfilesReloadSecureConsentAndMultipleTabsStayIsolated(@TempDir Path directory) throws Exception {
        var credentials = io.datacraft.platform.PlatformStores.credentials();
        var profileService = new io.datacraft.core.application.ConnectionProfiles(
                new io.datacraft.platform.FileConnectionRepository(directory), credentials);
        java.util.UUID savedId = null;
        try {
            fx(window::shutdown).get(15, TimeUnit.SECONDS);
            fx(() -> {
                stage.hide();
                window = new DesktopWindow(new DesktopController(new WorkspaceService(
                        new io.datacraft.core.adapter.AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, new PostgreSqlAdapter()))), profileService));
                stage.setScene(new Scene(window.root(), 1240, 820)); stage.show(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Choose a saved"));
            fx(() -> {
                var choice = (ComboBox<ConnectionPane.PasswordChoice>) node("password-storage", ComboBox.class);
                assertEquals(ConnectionPane.PasswordChoice.DO_NOT_STORE, choice.getValue());
                node("profile-name", TextField.class).setText("Local PostgreSQL");
                node("host", TextField.class).setText(POSTGRES.getHost());
                node("port", TextField.class).setText(Integer.toString(POSTGRES.getMappedPort(5432)));
                node("database", TextField.class).setText(POSTGRES.getDatabaseName());
                node("username", TextField.class).setText(POSTGRES.getUsername());
                node("password", PasswordField.class).setText(POSTGRES.getPassword());
                ((ComboBox<TlsMode>) node("tls", ComboBox.class)).setValue(TlsMode.DISABLED);
                if (credentials.available()) choice.setValue(ConnectionPane.PasswordChoice.STORE_SECURELY);
                node("save-profile", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Profile saved."));
            var saved = profileService.list().getFirst(); savedId = saved.id();
            assertEquals(credentials.available(), saved.storePassword());
            assertFalse(Files.readString(directory.resolve("connections.properties")).contains(POSTGRES.getPassword()), "Profile file must not contain a password");
            // A new window/controller reads the persisted profile; no user AppData or credential is touched.
            fx(window::shutdown).get(15, TimeUnit.SECONDS);
            fx(() -> {
                stage.hide();
                window = new DesktopWindow(new DesktopController(new WorkspaceService(
                        new io.datacraft.core.adapter.AdapterRegistry(java.util.Map.of(DatabaseKind.POSTGRESQL, new PostgreSqlAdapter()))), profileService));
                stage.setScene(new Scene(window.root(), 1240, 820)); stage.show(); return null;
            });
            await(() -> node("saved-connections", ComboBox.class).getItems().size() == 1);
            fx(() -> {
                ((ComboBox<SavedConnection>) node("saved-connections", ComboBox.class)).setValue(saved);
                assertEquals("", node("password", PasswordField.class).getText());
                if (!credentials.available()) node("password", PasswordField.class).setText(POSTGRES.getPassword());
                node("connect", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Connected."));
            var first = fx(window::activeQuery);
            fx(() -> { first.editor.setText("SELECT 11 AS first_result"); first.rowLimit.getValueFactory().setValue(2); first.run.fire(); return null; });
            await(() -> first.results.getItems().size() == 1);
            fx(() -> { node("new-query", Button.class).fire(); return null; });
            var second = fx(window::activeQuery);
            assertNotSame(first, second);
            fx(() -> {
                assertEquals(500, second.rowLimit.getValue()); assertEquals(2, first.rowLimit.getValue());
                assertTrue(second.results.getItems().isEmpty());
                second.editor.setText("SELECT 22 AS second_result"); second.run.fire(); return null;
            });
            await(() -> second.results.getItems().size() == 1);
            await(() -> tabHeader(second).getBoundsInParent().getWidth() >= 100);
            fx(() -> {
                assertEquals("11", first.results.getItems().getFirst().getFirst().value());
                assertEquals("22", second.results.getItems().getFirst().getFirst().value());
                snapshot("multi-tab-workspace.png");
                second.editor.setText("SELECT pg_sleep(30)"); second.run.fire();
                node("query-tabs", TabPane.class).getSelectionModel().select(first.tab);
                assertTrue(first.cancel.isDisabled()); assertFalse(second.cancel.isDisabled());
                assertTrue(first.run.isDisabled());
                var close = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
                second.tab.getOnCloseRequest().handle(close); assertTrue(close.isConsumed());
                second.cancel.fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Query cancelled."));
            fx(() -> { node("new-query", Button.class).fire(); return null; });
            var clean = fx(window::activeQuery);
            await(() -> tabHeader(clean).getBoundsInParent().getWidth() >= 90);
            fx(() -> {
                tabHeader(clean).lookup(".tab-close-button").fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED,
                        0, 0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        true, false, false, false, false, true, null));
                assertFalse(node("query-tabs", TabPane.class).getTabs().contains(clean.tab));
                return null;
            });
            fx(() -> {
                assertEquals("11", first.results.getItems().getFirst().getFirst().value());
                assertEquals("No result", second.resultSummary.getText());
                // Edited tabs and window closure require an explicit discard choice.
                Platform.runLater(() -> clickDialog(ButtonType.CANCEL));
                var close = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
                first.tab.getOnCloseRequest().handle(close); assertTrue(close.isConsumed());
                Platform.runLater(() -> clickDialog(ButtonType.CANCEL));
                assertFalse(window.confirmClose());
                node("disconnect", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Disconnected."));
            fx(() -> {
                assertTrue(first.results.getItems().isEmpty()); assertTrue(second.results.getItems().isEmpty());
                assertEquals("SELECT 11 AS first_result", first.editor.getText());
                assertTrue(first.run.isDisabled());
                if (credentials.available()) {
                    ((ComboBox<ConnectionPane.PasswordChoice>) node("password-storage", ComboBox.class)).setValue(ConnectionPane.PasswordChoice.DO_NOT_STORE);
                    node("save-profile", Button.class).fire();
                }
                return null;
            });
            if (credentials.available()) {
                await(() -> node("status", Label.class).getText().startsWith("Profile saved without"));
                assertTrue(credentials.read(savedId).isEmpty());
            }
            fx(() -> {
                node("profile-name", TextField.class).setText("Renamed PostgreSQL");
                node("save-profile", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Profile saved without"));
            assertEquals("Renamed PostgreSQL", profileService.list().getFirst().name());
            fx(() -> {
                Platform.runLater(() -> clickDialog(ButtonType.OK));
                node("delete-profile", Button.class).fire(); return null;
            });
            await(() -> node("status", Label.class).getText().startsWith("Connection profile and"));
            assertTrue(profileService.list().isEmpty());
            fx(() -> { assertEquals("", node("profile-name", TextField.class).getText()); return null; });
        } finally {
            fx(window::shutdown).get(15, TimeUnit.SECONDS);
            for (var profile : profileService.list()) profileService.delete(profile.id());
        }
    }

    private static void verifySqlIntelligence(String relation, String screenshot) throws Exception {
        var query = fx(window::activeQuery);
        fx(() -> {
            node("query-tabs", TabPane.class).getSelectionModel().select(query.tab);
            query.editor.requestFocus();
            query.editor.setText("SELECT w. FROM " + relation + " w");
            query.editor.positionCaret(9);
            query.editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, true, false, false));
            return null;
        });
        await(() -> isShowing(query.sqlSupport.suggestions));
        fx(() -> {
            var item = query.sqlSupport.suggestions.getItems().stream().filter(value -> value.label().equals("id")).findFirst().orElseThrow();
            query.sqlSupport.suggestions.getSelectionModel().select(item);
            query.editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertTrue(query.editor.getText().contains(item.insertText()), "Caret after completion: " + query.editor.getCaretPosition()
                    + "; SQL: " + query.editor.getText());
            assertFalse(isShowing(query.sqlSupport.suggestions));
            return null;
        });
        await(() -> query.sqlSupport.diagnostics.getText().startsWith("Parsed"));
        fx(() -> {
            assertNotNull(query.sqlSupport.ast.getRoot());
            var column = findAstColumn(query.sqlSupport.ast.getRoot());
            assertNotNull(column);
            query.sqlSupport.inspector.setExpanded(true);
            query.sqlSupport.ast.getSelectionModel().select(column);
            assertTrue(query.editor.getSelectedText().startsWith("w."));
            return null;
        });
        await(() -> isShowing(query.sqlSupport.ast) && query.sqlSupport.ast.getHeight() >= 60);
        fx(() -> {
            if (screenshot != null) snapshot(screenshot);
            query.sqlSupport.inspector.setExpanded(false);
            query.editor.setText("SELECT FROM"); return null;
        });
        await(() -> query.sqlSupport.diagnostics.getText().contains("Incomplete SQL"));
        fx(() -> {
            query.editor.setText("SELECT w. FROM " + relation + " w"); query.editor.positionCaret(9);
            query.sqlSupport.requestCompletion(); query.editor.setText("SELECT 42 AS changed");
            return null;
        });
        await(() -> query.sqlSupport.diagnostics.getText().startsWith("Parsed"));
        fx(() -> { assertFalse(isShowing(query.sqlSupport.suggestions)); assertEquals("SELECT 42 AS changed", query.editor.getText()); return null; });
        fx(() -> {
            query.editor.setText("SELECT w.id FROM " + relation + " w"); query.editor.positionCaret(9);
            query.sqlSupport.requestCompletion(); return null;
        });
        await(() -> isShowing(query.sqlSupport.suggestions));
        fx(() -> {
            query.editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, true, false, false));
            assertFalse(isShowing(query.sqlSupport.suggestions)); return null;
        });
        await(() -> !query.run.isDisabled() && !query.results.getItems().isEmpty());
    }
    private static TreeItem<io.datacraft.sql.SqlAnalysis.AstNode> findAstColumn(TreeItem<io.datacraft.sql.SqlAnalysis.AstNode> item) {
        if (item.getValue() != null && item.getValue().kind().equals("Column")) return item;
        for (var child : item.getChildren()) { var found = findAstColumn(child); if (found != null) return found; }
        return null;
    }
    private static void clickDialog(ButtonType button) {
        var dialog = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElseThrow();
        ((Button) dialog.lookupButton(button)).fire();
    }
    private static javafx.scene.Node tabHeader(QueryPane query) {
        return node("query-tabs", TabPane.class).lookupAll(".tab").stream().filter(header -> {
            var label = header.lookup(".tab-label");
            return label instanceof Label title && title.getText().equals(query.tab.getText());
        }).findFirst().orElseThrow();
    }

}
