/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import io.datacraft.core.query.*;

/** One query document and its presentation; never owns a database session. */
final class QueryPane {
    private static final PseudoClass NULL_VALUE = PseudoClass.getPseudoClass("null-value");
    final TextArea editor = new TextArea();
    final Spinner<Integer> rowLimit = new Spinner<>(1, 1000, 500);
    final Spinner<Integer> queryTimeout = new Spinner<>(1, 300, 30);
    final Button run = button("Run query", "run");
    final Button cancel = button("Cancel", "cancel");
    final TableView<List<QueryCell>> results = new TableView<>();
    final Label resultSummary = new Label("No query results yet");
    final Tab tab;
    private final String title;
    private String initialSql;
    private final VBox intelligence = new VBox();
    private final VBox editorPanel = new VBox();
    SqlEditorSupport sqlSupport;

    QueryPane(int number, String sql, Consumer<QueryPane> execute, Runnable cancelQuery) {
        title = "Query " + number;
        initialSql = sql;
        editor.setText(sql);
        tab = new Tab(title, buildView());
        tab.setClosable(true);
        tab.setUserData(this);
        run.getStyleClass().add("primary");
        run.setOnAction(event -> execute.accept(this));
        cancel.setOnAction(event -> cancelQuery.run());
        editor.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.ENTER) {
                execute.accept(this); event.consume();
            }
        });
        editor.textProperty().addListener((ignored, before, after) -> tab.setText(title + (dirty() ? " •" : "")));
    }
    boolean dirty() { return !editor.getText().equals(initialSql); }
    void enableSqlSupport(DesktopController controller, java.util.function.Supplier<io.datacraft.core.connection.DatabaseKind> kind) {
        sqlSupport = new SqlEditorSupport(this, controller, kind);
        intelligence.getChildren().setAll(sqlSupport.diagnostics, sqlSupport.inspector);
        sqlSupport.inspector.expandedProperty().addListener((ignored, before, expanded) -> editorPanel.setMinHeight(expanded ? 240 : 120));
    }
    void close() { if (sqlSupport != null) sqlSupport.close(); }
    void invalidateAnalysis() { if (sqlSupport != null) sqlSupport.invalidate(); }
    void replaceExample(String sql) {
        if (!dirty()) { initialSql = sql; editor.setText(sql); tab.setText(title); }
    }
    QueryRequest request() {
        String sql = editor.getSelectedText().isBlank() ? editor.getText() : editor.getSelectedText();
        return new QueryRequest(sql, rowLimit.getValue(), 4096, queryTimeout.getValue());
    }
    void clearResults() {
        results.getItems().clear(); results.getColumns().clear(); resultSummary.setText("No query results yet");
    }
    void updateControls(boolean connected, boolean busy, boolean owner, boolean closing) {
        run.setDisable(!connected || busy || closing);
        cancel.setDisable(!owner || closing);
        rowLimit.setDisable(owner || closing); queryTimeout.setDisable(owner || closing);
        editor.setDisable(closing);
    }
    private static Button button(String title, String id) {
        var button = new Button(title); button.setId(id); return button;
    }
    private VBox buildView() {
        rowLimit.setId("row-limit"); rowLimit.setPrefWidth(80);
        queryTimeout.setId("query-timeout"); queryTimeout.setPrefWidth(80);
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        var rowsLabel = new Label("Row limit"); rowsLabel.getStyleClass().add("toolbar-label");
        var timeoutLabel = new Label("Timeout (s)"); timeoutLabel.getStyleClass().add("toolbar-label");
        var toolbar = new HBox(run, cancel, spacer, rowsLabel, rowLimit, timeoutLabel, queryTimeout);
        toolbar.getStyleClass().add("query-toolbar");
        editor.setId("editor"); editor.setWrapText(false); editor.setMinHeight(40);
        editor.getStyleClass().add("sql-editor");
        var editorTitle = new Label("QUERY"); editorTitle.getStyleClass().add("section-title");
        var editorSpacer = new Region(); HBox.setHgrow(editorSpacer, Priority.ALWAYS);
        var shortcut = new Label("Ctrl+Space · completion   |   Ctrl / ⌘ + Enter · run"); shortcut.getStyleClass().add("shortcut-hint");
        var editorHeading = new HBox(editorTitle, editorSpacer, shortcut); editorHeading.getStyleClass().add("editor-heading");
        editorPanel.getChildren().setAll(editorHeading, editor, intelligence); editorPanel.setMinHeight(120);
        VBox.setVgrow(editor, Priority.ALWAYS);
        results.setId("results");
        var emptyResults = new Label("Run a SELECT to see results");
        emptyResults.getStyleClass().add("results-placeholder");
        results.setPlaceholder(emptyResults);
        resultSummary.getStyleClass().add("result-summary");
        var resultTitle = new Label("RESULTS"); resultTitle.getStyleClass().add("section-title");
        var resultSpacer = new Region(); HBox.setHgrow(resultSpacer, Priority.ALWAYS);
        var resultHeading = new HBox(resultTitle, resultSpacer, resultSummary); resultHeading.getStyleClass().add("result-heading");
        results.setMinHeight(50);
        var resultPanel = new VBox(resultHeading, results); resultPanel.setMinHeight(90);
        VBox.setVgrow(results, Priority.ALWAYS);
        var editorAndResults = new SplitPane(editorPanel, resultPanel);
        editorAndResults.getStyleClass().add("editor-results");
        editorAndResults.setOrientation(javafx.geometry.Orientation.VERTICAL);
        editorAndResults.setDividerPositions(0.40);
        var content = new VBox(toolbar, editorAndResults); content.getStyleClass().add("query-pane");
        VBox.setVgrow(editorAndResults, Priority.ALWAYS);
        return content;
    }
    void showResult(QueryResult result) {
        results.getColumns().clear();
        for (int index = 0; index < result.columns().size(); index++) {
            final int position = index;
            var metadata = result.columns().get(index);
            var column = new TableColumn<List<QueryCell>, QueryCell>(metadata.label() + "\n" + metadata.databaseType());
            column.setPrefWidth(180);
            column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().get(position)));
            column.setCellFactory(ignored -> new TableCell<>() {
                @Override protected void updateItem(QueryCell cell, boolean empty) {
                    super.updateItem(cell, empty);
                    setText(null); setTooltip(null); pseudoClassStateChanged(NULL_VALUE, false);
                    if (empty || cell == null) return;
                    if (cell.isNull()) {
                        setText("NULL"); pseudoClassStateChanged(NULL_VALUE, true);
                        setTooltip(new Tooltip("SQL NULL"));
                    } else {
                        setText(cell.value() + (cell.truncated() ? " …" : ""));
                        if (cell.truncated()) setTooltip(new Tooltip("Display value truncated at 4096 characters."));
                        else if (cell.value().isEmpty()) setTooltip(new Tooltip("Empty text; not NULL."));
                    }
                }
            });
            results.getColumns().add(column);
        }
        results.getItems().setAll(result.rows());
        resultSummary.setText(result.rows().size() + " rows  ·  " + result.elapsedMillis() + " ms"
                + (result.truncated() ? "  ·  Truncated" : ""));
    }
}
