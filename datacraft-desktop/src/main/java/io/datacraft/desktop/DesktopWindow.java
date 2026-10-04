/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.util.List;
import java.util.Objects;
import javafx.css.PseudoClass;
import javafx.scene.shape.Circle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import io.datacraft.core.adapter.DatabaseException;
import io.datacraft.core.connection.*;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;

/** JavaFX presentation only; all database work goes through application services. */
public final class DesktopWindow {
    private static final PseudoClass CONNECTED = PseudoClass.getPseudoClass("connected");
    private static final PseudoClass PRODUCTION = PseudoClass.getPseudoClass("production");
    private static final PseudoClass BUSY = PseudoClass.getPseudoClass("busy");
    private final DesktopController controller;
    private final BorderPane root = new BorderPane();
    private final ConnectionPane connectionPane;
    private final Button connect = button("Connect", "connect");
    private final Button disconnect = button("Disconnect", "disconnect");
    private final Button refresh = button("Refresh", "refresh");
    private final Label badge = new Label("DISCONNECTED");
    private final Circle statusDot = new Circle(4);
    private final Label status = new Label("Choose a saved connection or enter details. Password storage is optional.");
    private final Label object = new Label("Select a relation to inspect its columns.");
    private final TreeView<ExplorerNode> explorer = new TreeView<>();
    private final TableView<ColumnMetadata> columns = new TableView<>();
    private final Tab columnsTab = new Tab("Columns");
    private final TabPane tabs = new TabPane(columnsTab);
    private final VBox welcome = emptyState("◇", "Connect to start your workspace", "Choose a saved connection or define a new one above.\nYour database hierarchy and query tabs will open here.");
    private final java.util.ArrayList<QueryPane> queries = new java.util.ArrayList<>();
    private final Button newQuery = button("+ Query", "new-query");
    private QueryPane activeQuery;
    private QueryPane runningQuery;
    private int queryNumber;
    private boolean connected;
    private boolean busy;
    private boolean querying;
    private boolean closing;

    public DesktopWindow(DesktopController controller) {
        this.controller = controller;
        root.setId("workspace");
        root.getStyleClass().add("workspace");
        root.getStylesheets().add(Objects.requireNonNull(DesktopWindow.class.getResource("workspace.css"),
                "Workspace stylesheet is required.").toExternalForm());
        connect.getStyleClass().add("primary");
        refresh.getStyleClass().add("quiet");
        var title = new Label("DataCraft");
        title.getStyleClass().add("brand-title");
        var subtitle = new Label();
        connectionPane = new ConnectionPane(controller.availableDatabases(), connect, disconnect, selected -> {
            subtitle.setText(selected + "  /  read-only workspace");
            queries.forEach(query -> query.replaceExample(example(selected)));
            queries.forEach(QueryPane::invalidateAnalysis);
        });
        connectionPane.configureProfiles(controller.profilesAvailable(), controller.canStorePassword(), this::saveProfile, this::deleteProfile);
        subtitle.getStyleClass().add("brand-subtitle");
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        var mark = new Label("D"); mark.getStyleClass().add("brand-mark");
        badge.setId("environment-badge"); badge.getStyleClass().add("environment-badge");
        var heading = new HBox(12, mark, new VBox(2, title, subtitle), spacer, badge);
        heading.getStyleClass().add("app-heading");
        var top = new VBox(heading, connectionPane.root());
        VBox.setMargin(connectionPane.root(), new Insets(16, 18, 14, 18));
        root.setTop(top);
        columnsTab.setClosable(false);
        tabs.setId("query-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        newQuery.getStyleClass().add("quiet");
        newQuery.setOnAction(event -> addQuery());
        tabs.getSelectionModel().selectedItemProperty().addListener((ignored, before, selected) -> {
            if (selected != null && selected.getUserData() instanceof QueryPane query) activeQuery = query;
        });
        addQuery();
        columnsTab.setContent(columnPane());
        explorer.setId("explorer");
        explorer.setShowRoot(true);
        explorer.setCellFactory(ignored -> new TreeCell<>() {
            @Override protected void updateItem(ExplorerNode node, boolean empty) {
                super.updateItem(node, empty);
                setText(null); setGraphic(null); setTooltip(null);
                if (empty || node == null) return;
                setText(node.label());
                setTooltip(new Tooltip(node.label()));
                if (!node.placeholder()) {
                    var icon = new Label(node.relation() == null ? "◇" : "▦");
                    icon.getStyleClass().add("object-icon"); setGraphic(icon);
                }
            }
        });
        explorer.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, selected) -> {
            if (selected == null || busy || closing) return;
            var node = selected.getValue();
            if (node.relation() != null) {
                perform(controller.columns(node.relation()), values -> {
                    columns.getItems().setAll(values);
                    object.setText(node.schema() + " / " + node.relation().name());
                    tabs.getSelectionModel().select(columnsTab);
                    status.setText(values.size() + " columns · metadata refreshed");
                }, "Loading columns…", false);
            } else if (node.schema() != null && !node.placeholder()) selected.setExpanded(true);
        });
        var explorerTitle = new Label("EXPLORER"); explorerTitle.getStyleClass().add("section-title");
        var explorerSpacer = new Region(); HBox.setHgrow(explorerSpacer, Priority.ALWAYS);
        var explorerHeading = new HBox(explorerTitle, explorerSpacer, newQuery, refresh);
        explorerHeading.getStyleClass().add("panel-heading");
        var emptyExplorer = emptyState("◇", "Your database, at a glance", "Connect to browse tables, views\nand column definitions.");
        emptyExplorer.setMouseTransparent(true);
        emptyExplorer.visibleProperty().bind(explorer.rootProperty().isNull());
        var treeArea = new StackPane(explorer, emptyExplorer);
        var sidebar = new VBox(explorerHeading, treeArea);
        sidebar.getStyleClass().add("sidebar"); sidebar.setMinWidth(230);
        VBox.setVgrow(treeArea, Priority.ALWAYS);
        var split = new SplitPane(sidebar, new StackPane(tabs, welcome)); split.getStyleClass().add("workspace-split");
        split.setDividerPositions(0.23);
        BorderPane.setMargin(split, new Insets(0, 18, 14, 18));
        root.setCenter(split);
        status.setId("status"); status.setWrapText(true); status.getStyleClass().add("status-text");
        statusDot.getStyleClass().add("status-dot");
        var footer = new HBox(statusDot, status); footer.getStyleClass().add("status-bar");
        root.setBottom(footer);
        connect.setOnAction(event -> connect());
        disconnect.setOnAction(event -> perform(controller.disconnect(), ignored -> clearConnection(), "Disconnecting…", false));
        refresh.setOnAction(event -> perform(controller.schemas(), this::showSchemas, "Refreshing schemas…", false));
        updateControls();
        if (controller.profilesAvailable()) perform(controller.profiles(), profiles -> {
            connectionPane.setProfiles(profiles, null);
            status.setText("Choose a saved connection or enter details. Password storage is optional.");
        }, "Loading saved connections…", false);
    }

    public Parent root() { return root; }
    private static Button button(String label, String id) {
        var button = new Button(label); button.setId(id); return button;
    }
    private static VBox emptyState(String symbol, String title, String detail) {
        var icon = new Label(symbol); icon.getStyleClass().add("empty-symbol");
        var heading = new Label(title); heading.getStyleClass().add("empty-title");
        var caption = new Label(detail); caption.setWrapText(true); caption.getStyleClass().add("empty-caption");
        var view = new VBox(icon, heading, caption); view.getStyleClass().add("empty-state");
        return view;
    }
    private static String example(DatabaseKind kind) {
        return switch (kind) {
            case SQLITE -> "SELECT sqlite_version() AS version;";
            case POSTGRESQL -> "SELECT current_database() AS database, current_user AS username;";
            case MYSQL, MARIADB -> "SELECT DATABASE() AS database, CURRENT_USER() AS username;";
        };
    }
    private void addQuery() {
        if (closing || queries.size() >= 20) return;
        var query = new QueryPane(++queryNumber, example(connectionPane.kind()), this::execute, () -> {
            controller.cancel();
            if (runningQuery != null) runningQuery.cancel.setDisable(true);
            status.setText("Cancellation requested…");
        });
        query.enableSqlSupport(controller, connectionPane::kind);
        query.tab.setOnCloseRequest(event -> {
            if (query == runningQuery || !confirmDiscard(query.dirty())) event.consume();
        });
        query.tab.setOnClosed(event -> {
            query.close();
            queries.remove(query);
            if (queries.isEmpty()) addQuery();
            else if (activeQuery == query) {
                activeQuery = queries.getLast(); tabs.getSelectionModel().select(activeQuery.tab);
            }
            updateControls();
        });
        queries.add(query); tabs.getTabs().add(tabs.getTabs().size() - 1, query.tab);
        activeQuery = query; tabs.getSelectionModel().select(query.tab); updateControls();
    }
    QueryPane activeQuery() { return activeQuery; }
    private boolean confirmDiscard(boolean dirty) {
        if (!dirty) return true;
        var dialog = new Alert(Alert.AlertType.CONFIRMATION,
                "Query text is kept only in this window. Discard edited queries?", ButtonType.CANCEL, ButtonType.OK);
        dialog.initOwner(root.getScene().getWindow()); dialog.setTitle("Discard query edits");
        dialog.setHeaderText("Unsaved query text");
        return dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
    public boolean confirmClose() { return confirmDiscard(queries.stream().anyMatch(QueryPane::dirty)); }
    private VBox columnPane() {
        columns.setId("columns"); columns.setPlaceholder(emptyState("▦", "Explore a relation", "Select a table or view to see its column definitions."));
        addColumn("Name", column -> column.name());
        addColumn("Position", column -> Integer.toString(column.ordinal()));
        addColumn("Type", column -> column.databaseType());
        addColumn("Nullable", column -> column.nullable() ? "Yes" : "No");
        object.getStyleClass().add("panel-heading");
        var content = new VBox(object, columns); content.getStyleClass().add("columns-pane"); VBox.setVgrow(columns, Priority.ALWAYS);
        return content;
    }
    private void addColumn(String title, java.util.function.Function<ColumnMetadata, String> value) {
        var column = new TableColumn<ColumnMetadata, String>(title);
        column.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
        column.setPrefWidth(title.equals("Name") || title.equals("Type") ? 230 : 100);
        columns.getColumns().add(column);
    }
    private void connect() {
        if (busy || connected || closing) return;
        try {
            var settings = connectionPane.profile();
            var saved = connectionPane.named() ? connectionPane.savedProfile() : null;
            char[] buffer = connectionPane.takePassword();
            perform(saved == null ? controller.connect(settings, buffer) : controller.connectProfile(saved, buffer), schemas -> {
                connected = true;
                queries.forEach(QueryPane::invalidateAnalysis);
                badge.setText(settings.environment().name() + "  ·  READ ONLY");
                badge.pseudoClassStateChanged(CONNECTED, true);
                badge.pseudoClassStateChanged(PRODUCTION, settings.environment() == Environment.PRODUCTION);
                showSchemas(schemas);
                tabs.getSelectionModel().select(activeQuery.tab);
                status.setText("Connected. Choose a schema or run a query.");
                updateControls();
                if (saved != null) refreshProfiles(saved.id());
            }, "Connecting…", false);
        } catch (IllegalArgumentException failure) {
            connectionPane.clearPassword(); status.setText("Check the connection fields and select an existing database file for SQLite.");
        }
    }
    private void refreshProfiles(java.util.UUID selected) {
        controller.profiles().whenComplete((profiles, failure) -> Platform.runLater(() -> {
            if (closing) return;
            if (failure == null) connectionPane.setProfiles(profiles, selected);
            else showFailure(failure);
            updateControls();
        }));
    }
    private void saveProfile() {
        if (connected || busy || closing) return;
        try {
            var profile = connectionPane.savedProfile();
            perform(controller.saveProfile(profile, connectionPane.takePassword()), profiles -> {
                connectionPane.setProfiles(profiles, profile.id());
                status.setText(profile.storePassword() ? "Profile saved. Password stored in Windows Credential Manager."
                        : "Profile saved without a password. Enter a password to connect if required.");
            }, "Saving connection profile…", false);
        } catch (IllegalArgumentException failure) {
            connectionPane.clearPassword(); status.setText("Enter a connection name and valid connection details.");
        }
    }
    private void deleteProfile() {
        if (connected || busy || closing || connectionPane.selectedId() == null) return;
        var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this connection profile and its stored password?", ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(root.getScene().getWindow()); confirm.setHeaderText("Delete saved connection");
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        perform(controller.deleteProfile(connectionPane.selectedId()), profiles -> {
            connectionPane.setProfiles(profiles, null); connectionPane.clearPassword();
            status.setText("Connection profile and its stored password deleted.");
        }, "Deleting connection profile…", false);
    }
    private void showSchemas(List<String> schemas) {
        var treeRoot = new TreeItem<>(new ExplorerNode(connectionPane.displayName(), null, null, false));
        for (String schema : schemas) {
            var item = new TreeItem<>(new ExplorerNode(schema, schema, null, false));
            item.getChildren().add(new TreeItem<>(new ExplorerNode("Expand to load relations", null, null, true)));
            item.expandedProperty().addListener((ignored, oldValue, expanded) -> {
                if (!expanded || busy || closing) return;
                perform(controller.relations(schema), relations -> {
                    item.getChildren().clear();
                    for (var relation : relations) item.getChildren().add(new TreeItem<>(new ExplorerNode(
                            relation.name().name() + "  ·  " + relation.kind().name().toLowerCase().replace('_', ' '), schema, relation.name(), false)));
                    status.setText(relations.size() + " relations in " + schema);
                }, "Loading relations…", false);
            });
            treeRoot.getChildren().add(item);
        }
        explorer.setRoot(treeRoot);
        treeRoot.setExpanded(true);
        columns.getItems().clear();
    }
    private void execute(QueryPane query) {
        if (!connected || busy || closing) return;
        try {
            var request = query.request();
            query.clearResults(); query.resultSummary.setText("Running…");
            tabs.getSelectionModel().select(query.tab); runningQuery = query;
            perform(controller.query(request), result -> {
                query.showResult(result);
                status.setText(result.rows().size() + " rows · " + result.elapsedMillis() + " ms"
                        + (result.truncated() ? " · Results truncated by display limits" : " · Complete"));
            }, "Running read-only query…", true);
        } catch (IllegalArgumentException failure) { status.setText("Enter a SELECT of at most 100000 characters."); }
    }
    private <T> void perform(CompletableFuture<T> work, Consumer<T> success, String message, boolean query) {
        busy = true; querying = query; status.setText(message); updateControls();
        work.whenComplete((value, failure) -> Platform.runLater(() -> {
            if (closing) return;
            busy = false; querying = false;
            if (failure == null) success.accept(value);
            else {
                if (query && runningQuery != null) runningQuery.resultSummary.setText("No result");
                showFailure(failure);
            }
            if (query) runningQuery = null;
            updateControls();
        }));
    }
    private void showFailure(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof DatabaseException databaseFailure) {
            status.setText(switch (databaseFailure.kind()) {
                case POLICY -> "Blocked: this MVP accepts one SELECT (including WITH … SELECT) in a read-only transaction.";
                case CANCELLED -> "Query cancelled. You can run another query.";
                case TIMEOUT -> "Query timed out. Try a shorter query or increase the timeout.";
                case AUTHENTICATION -> "Authentication failed. Check your database credentials.";
                case CONNECTION, CLOSED -> "Connection unavailable. Disconnect and reconnect; check the server settings or database file.";
                case PERMISSION -> "The database role does not have permission for this operation.";
                case NOT_FOUND -> "Database object not found. Refresh the explorer.";
                case RESULT_LIMIT -> "Result has too many columns or cannot be displayed. Select fewer columns.";
                case OTHER -> "Database operation failed. Check the SQL syntax and object names.";
            });
        } else if (failure instanceof java.io.IOException) {
            status.setText("Profile operation failed. Check the profile file, secure-storage availability, and password. Enter a password when saving changed connection settings.");
        } else status.setText("Operation failed. Check the connection and input settings.");
    }
    private void clearConnection() {
        connected = false; badge.setText("DISCONNECTED");
        badge.pseudoClassStateChanged(CONNECTED, false); badge.pseudoClassStateChanged(PRODUCTION, false);
        queries.forEach(QueryPane::clearResults);
        queries.forEach(QueryPane::invalidateAnalysis);
        connectionPane.clearPassword(); explorer.setRoot(null); columns.getItems().clear();
        object.setText("Select a relation to inspect its columns.");
        status.setText("Disconnected. Saved profiles are retained; query text remains in this window.");
    }
    private void updateControls() {
        statusDot.pseudoClassStateChanged(CONNECTED, connected);
        statusDot.pseudoClassStateChanged(BUSY, busy);
        connectionPane.setDisabled(connected || busy || closing);
        connectionPane.compact(connected);
        connect.setDisable(connected || busy || closing);
        disconnect.setDisable(!connected || busy || closing);
        refresh.setDisable(!connected || busy || closing);
        explorer.setDisable(!connected || busy || closing);
        tabs.setVisible(connected); tabs.setManaged(connected);
        welcome.setVisible(!connected); welcome.setManaged(!connected);
        newQuery.setDisable(!connected || closing || queries.size() >= 20);
        queries.forEach(query -> query.updateControls(connected, busy, query == runningQuery && querying, closing));
    }
    public CompletableFuture<Void> shutdown() {
        closing = true; root.setDisable(true); status.setText("Closing database connection…");
        queries.forEach(QueryPane::close);
        connectionPane.clearPassword(); return controller.shutdown();
    }
    public record ExplorerNode(String label, String schema, QualifiedName relation, boolean placeholder) {
        @Override public String toString() { return label; }
    }
}
