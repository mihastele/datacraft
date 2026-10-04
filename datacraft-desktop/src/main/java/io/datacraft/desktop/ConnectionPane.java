/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import io.datacraft.core.connection.*;

/** Connection presentation and input mapping; no driver, SQL, or connection lifecycle logic. */
final class ConnectionPane {
    private final VBox root;
    private final ComboBox<SavedConnection> saved = new ComboBox<>();
    private final TextField name = field("", "profile-name");
    private final ComboBox<PasswordChoice> passwordChoice = new ComboBox<>(FXCollections.observableArrayList(PasswordChoice.values()));
    private final Button save = new Button("Save"), delete = new Button("Delete"), fresh = new Button("New");
    private java.util.UUID selectedId;
    private boolean secureStorage;
    private boolean profilesEnabled;
    private boolean loading;
    enum PasswordChoice {
        DO_NOT_STORE("Do not store"), STORE_SECURELY("Store securely");
        private final String label;
        PasswordChoice(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private final GridPane server = new GridPane();
    private final HBox local;
    private final ComboBox<DatabaseKind> kind;
    private final TextField host = field("localhost", "host");
    private final TextField port = field("5432", "port");
    private final TextField database = field("postgres", "database");
    private final TextField username = field("", "username");
    private final PasswordField password = new PasswordField();
    private final TextField file = field("", "sqlite-file");
    private final ComboBox<Environment> environment = new ComboBox<>(FXCollections.observableArrayList(Environment.values()));
    private final ComboBox<TlsMode> tls = new ComboBox<>(FXCollections.observableArrayList(TlsMode.values()));
    private final Spinner<Integer> timeout = new Spinner<>(1, 300, 10);

    ConnectionPane(Set<DatabaseKind> available, Button connect, Button disconnect, Consumer<DatabaseKind> changed) {
        kind = new ComboBox<>(FXCollections.observableArrayList(available.stream().sorted().toList()));
        kind.setId("database-kind");
        password.setId("password"); password.setPromptText("Enter for this session");
        username.setPromptText("Database user"); file.setPromptText("Existing SQLite database file");
        environment.setId("environment"); environment.setValue(Environment.DEVELOPMENT);
        tls.setId("tls"); tls.setValue(TlsMode.VERIFY_FULL);
        timeout.setId("connection-timeout"); timeout.setPrefWidth(85); port.setPrefWidth(75);
        server.setHgap(12); server.setVgap(6);
        server.getStyleClass().add("server-fields");
        double[] widths = {21, 8, 19, 17, 17, 18};
        for (double width : widths) {
            var constraint = new ColumnConstraints(); constraint.setPercentWidth(width);
            server.getColumnConstraints().add(constraint);
        }
        for (var input : List.of(host, port, database, username, password)) input.setMinWidth(0);
        tls.setMaxWidth(Double.MAX_VALUE);
        var labels = List.of("Host", "Port", "Database", "Username", "Password", "TLS");
        var inputs = List.of(host, port, database, username, password, tls);
        for (int i = 0; i < labels.size(); i++) {
            var label = new Label(labels.get(i)); label.getStyleClass().add("field-label");
            server.add(label, i, 0); server.add(inputs.get(i), i, 1);
            GridPane.setHgrow(inputs.get(i), Priority.ALWAYS);
        }
        var browse = new Button("Browse…"); browse.setId("browse-sqlite");
        browse.setOnAction(event -> {
            var chooser = new FileChooser(); chooser.setTitle("Open existing SQLite database");
            var selected = chooser.showOpenDialog(root().getScene().getWindow());
            if (selected != null) file.setText(selected.getAbsolutePath());
        });
        file.setMinWidth(0);
        local = new HBox(12, fieldGroup("Database file", file), browse);
        local.getStyleClass().addAll("file-fields", "connection-options");
        HBox.setHgrow(local.getChildren().getFirst(), Priority.ALWAYS);
        kind.setPrefWidth(150); environment.setPrefWidth(150);
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        passwordChoice.setId("password-storage"); passwordChoice.setValue(PasswordChoice.DO_NOT_STORE);
        passwordChoice.setPrefWidth(140);
        var options = new HBox(fieldGroup("Database engine", kind), fieldGroup("Environment", environment),
                fieldGroup("Connect timeout (s)", timeout), fieldGroup("Password storage", passwordChoice), spacer, connect, disconnect);
        options.getStyleClass().add("connection-options");
        saved.setId("saved-connections"); saved.setPromptText("Choose a saved connection"); saved.setPrefWidth(210);
        name.setPromptText("Name to save this connection"); name.setPrefWidth(210);
        save.setId("save-profile"); delete.setId("delete-profile"); fresh.setId("new-profile");
        var profileBar = new HBox(12, fieldGroup("Saved connection", saved), fieldGroup("Connection name", name), fresh, save, delete);
        profileBar.getStyleClass().add("connection-options");
        saved.valueProperty().addListener((ignored, before, profile) -> {
            if (!loading && profile != null) load(profile);
        });
        fresh.setOnAction(event -> {
            selectedId = null; loading = true; saved.setValue(null); loading = false;
            name.clear(); clearPassword(); passwordChoice.setValue(PasswordChoice.DO_NOT_STORE);
            password.setPromptText("Enter for this session");
            host.setText("localhost"); port.setText(kind() == DatabaseKind.POSTGRESQL ? "5432" : "3306");
            database.setText(kind() == DatabaseKind.POSTGRESQL ? "postgres" : ""); username.clear(); file.clear();
            environment.setValue(Environment.DEVELOPMENT); tls.setValue(TlsMode.VERIFY_FULL);
            timeout.getValueFactory().setValue(10); delete.setDisable(true);
        });
        var caption = new Label("CONNECTIONS  /  Save basic details; storing a password is your choice");
        caption.getStyleClass().add("connection-caption");
        root = new VBox(caption, profileBar, options, server, local);
        root.getStyleClass().add("connection-card");
        kind.valueProperty().addListener((ignored, previous, selected) -> {
            boolean network = selected != DatabaseKind.SQLITE;
            if (selected != DatabaseKind.SQLITE) {
                if (port.getText().equals("5432") || port.getText().equals("3306")) port.setText(selected == DatabaseKind.POSTGRESQL ? "5432" : "3306");
                if (database.getText().equals("postgres") || database.getText().isBlank()) database.setText(selected == DatabaseKind.POSTGRESQL ? "postgres" : "");
            }
            server.setVisible(network); server.setManaged(network);
            local.setVisible(!network); local.setManaged(!network);
            clearPassword();
            passwordChoice.setDisable(!secureStorage || selected == DatabaseKind.SQLITE);
            if (selected == DatabaseKind.SQLITE) passwordChoice.setValue(PasswordChoice.DO_NOT_STORE);
            changed.accept(selected);
        });
        kind.setValue(available.contains(DatabaseKind.POSTGRESQL) ? DatabaseKind.POSTGRESQL : kind.getItems().getFirst());
    }
    VBox root() { return root; }
    DatabaseKind kind() { return kind.getValue(); }
    void configureProfiles(boolean enabled, boolean canStore, Runnable onSave, Runnable onDelete) {
        profilesEnabled = enabled; secureStorage = canStore;
        passwordChoice.setDisable(!canStore || kind() == DatabaseKind.SQLITE);
        if (!canStore) passwordChoice.setTooltip(new Tooltip("Secure password storage is available on Windows only. Enter a password for this session."));
        save.setOnAction(event -> onSave.run()); delete.setOnAction(event -> onDelete.run());
    }
    void setProfiles(List<SavedConnection> profiles, java.util.UUID selected) {
        loading = true;
        saved.getItems().setAll(profiles);
        saved.setValue(profiles.stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null));
        loading = false; selectedId = selected;
        if (selected == null) name.clear();
        password.setPromptText(saved.getValue() != null && saved.getValue().storePassword() && secureStorage
                ? "Leave blank to use stored password" : "Enter for this session");
        delete.setDisable(selected == null);
    }
    private void load(SavedConnection profile) {
        clearPassword(); selectedId = profile.id(); name.setText(profile.name());
        var settings = profile.settings(); kind.setValue(settings.kind());
        environment.setValue(settings.environment()); timeout.getValueFactory().setValue(settings.timeoutSeconds());
        if (settings instanceof SqliteConnectionSettings sqlite) file.setText(sqlite.file().toString());
        else if (settings instanceof ConnectionSettings network) {
            host.setText(network.host()); port.setText(Integer.toString(network.port())); database.setText(network.database());
            username.setText(network.username()); tls.setValue(network.tlsMode());
        }
        passwordChoice.setValue(profile.storePassword() ? PasswordChoice.STORE_SECURELY : PasswordChoice.DO_NOT_STORE);
        password.setPromptText(profile.storePassword() && secureStorage ? "Leave blank to use stored password" : "Enter for this session");
        delete.setDisable(false);
    }
    java.util.UUID selectedId() { return selectedId; }
    String displayName() {
        String label = name.getText().isBlank() ? kind().toString() : name.getText().strip();
        return kind() == DatabaseKind.SQLITE ? label : label + " / " + database.getText();
    }
    SavedConnection savedProfile() {
        return new SavedConnection(selectedId == null ? java.util.UUID.randomUUID() : selectedId,
                name.getText(), profile(), kind() != DatabaseKind.SQLITE && passwordChoice.getValue() == PasswordChoice.STORE_SECURELY);
    }
    boolean named() { return !name.getText().isBlank() || selectedId != null; }
    void setDisabled(boolean disabled) {
        kind.setDisable(disabled); server.setDisable(disabled); local.setDisable(disabled);
        environment.setDisable(disabled); timeout.setDisable(disabled);
        saved.setDisable(disabled || !profilesEnabled); name.setDisable(disabled || !profilesEnabled);
        fresh.setDisable(disabled || !profilesEnabled); save.setDisable(disabled || !profilesEnabled);
        delete.setDisable(disabled || !profilesEnabled || selectedId == null);
        passwordChoice.setDisable(disabled || !secureStorage || kind() == DatabaseKind.SQLITE);
    }
    void compact(boolean connected) {
        server.setVisible(!connected && kind() != DatabaseKind.SQLITE); server.setManaged(server.isVisible());
        local.setVisible(!connected && kind() == DatabaseKind.SQLITE); local.setManaged(local.isVisible());
    }
    ConnectionProfile profile() {
        if (kind() == DatabaseKind.SQLITE) {
            if (file.getText().isBlank()) throw new IllegalArgumentException("Select an existing database file.");
            return new SqliteConnectionSettings(Path.of(file.getText()), environment.getValue(), timeout.getValue());
        }
        return new ConnectionSettings(kind(), host.getText(), Integer.parseInt(port.getText()), database.getText(),
                username.getText(), environment.getValue(), tls.getValue(), timeout.getValue());
    }
    char[] takePassword() {
        char[] buffer = kind() != DatabaseKind.SQLITE ? password.getText().toCharArray() : new char[0];
        clearPassword(); return buffer;
    }
    void clearPassword() { password.clear(); }
    private static VBox fieldGroup(String title, javafx.scene.Node input) {
        var label = new Label(title); label.getStyleClass().add("field-label");
        return new VBox(6, label, input);
    }
    private static TextField field(String value, String id) { var field = new TextField(value); field.setId(id); return field; }
}
