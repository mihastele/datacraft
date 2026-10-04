/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import io.datacraft.core.application.WorkspaceService;
import io.datacraft.postgresql.PostgreSqlAdapter;
import io.datacraft.sqlite.SqliteAdapter;
import io.datacraft.mysql.MySqlMariaDbAdapter;
import io.datacraft.core.adapter.AdapterRegistry;
import io.datacraft.core.connection.DatabaseKind;
import java.util.Map;

/** Composition root: the only desktop class selecting a concrete database adapter. */
public final class DataCraftApp extends Application {
    private DesktopWindow window;
    @Override public void start(Stage stage) {
        var mysql = new MySqlMariaDbAdapter();
        var registry = new AdapterRegistry(Map.of(DatabaseKind.POSTGRESQL, new PostgreSqlAdapter(),
                DatabaseKind.SQLITE, new SqliteAdapter(), DatabaseKind.MYSQL, mysql, DatabaseKind.MARIADB, mysql));
        var controller = new DesktopController(new WorkspaceService(registry), io.datacraft.platform.PlatformStores.profiles());
        window = new DesktopWindow(controller);
        stage.setTitle("DataCraft");
        stage.setScene(new Scene(window.root(), 1240, 820));
        stage.setMinWidth(980);
        stage.setMinHeight(650);
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (!window.confirmClose()) return;
            window.shutdown().whenComplete((ignored, failure) -> Platform.runLater(() -> {
                stage.hide();
                Platform.exit();
            }));
        });
        stage.show();
        if (getParameters().getRaw().contains("--verify-launch")) {
            Platform.runLater(() -> window.shutdown().whenComplete((ignored, failure) -> Platform.runLater(() -> {
                stage.hide();
                Platform.exit();
            })));
        }
    }
}
