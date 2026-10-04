/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.util.Duration;
import io.datacraft.core.connection.DatabaseKind;
import io.datacraft.sql.*;

/** Per-document JavaFX binding: debounce, revision checks, popup and AST navigation. */
final class SqlEditorSupport {
    final Label diagnostics = new Label("SQL analysis is advisory");
    final TreeView<SqlAnalysis.AstNode> ast = new TreeView<>();
    final TitledPane inspector = new TitledPane("SQL structure", ast);
    final ListView<SqlCompletion.Item> suggestions = new ListView<>();
    private final Popup popup = new Popup();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(300));
    private final QueryPane query;
    private final DesktopController controller;
    private final Supplier<DatabaseKind> kind;
    private long revision;
    private boolean pending, completing, disposed;
    private SqlCompletion completion;
    private String completionText;
    private int completionCaret;
    private SchemaSnapshot completionMetadata;

    SqlEditorSupport(QueryPane query, DesktopController controller, Supplier<DatabaseKind> kind) {
        this.query = query; this.controller = controller; this.kind = kind;
        diagnostics.setId("sql-diagnostics"); diagnostics.getStyleClass().add("shortcut-hint");
        diagnostics.getStyleClass().add("sql-diagnostics");
        diagnostics.setTooltip(new Tooltip("Editor grammar is advisory. Expand a schema and inspect a relation to cache table/column completions. Ctrl+Space opens suggestions."));
        ast.setId("sql-ast"); ast.setShowRoot(false); ast.setPrefHeight(140); ast.setMinHeight(60);
        ast.setMaxHeight(140); inspector.setId("sql-inspector"); inspector.setExpanded(false);
        inspector.setAnimated(false); inspector.getStyleClass().add("sql-inspector");
        suggestions.setId("sql-completions"); suggestions.setPrefSize(420, 200);
        suggestions.getStyleClass().add("sql-completions");
        suggestions.getStylesheets().add(query.editor.getScene() == null
                ? SqlEditorSupport.class.getResource("workspace.css").toExternalForm()
                : query.editor.getScene().getStylesheets().getFirst());
        popup.getContent().add(suggestions); popup.setAutoHide(true); popup.setHideOnEscape(true);
        debounce.setOnFinished(event -> analyze());
        query.editor.textProperty().addListener((ignored, before, after) -> invalidate());
        query.editor.caretPositionProperty().addListener((ignored, before, after) -> popup.hide());
        query.tab.selectedProperty().addListener((ignored, before, after) -> { if (!after) popup.hide(); });
        query.editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.isControlDown() || event.isShortcutDown()) && event.getCode() == KeyCode.SPACE) {
                requestCompletion(); event.consume();
            } else if (popup.isShowing() && !event.isShortcutDown()) {
                if (event.getCode() == KeyCode.DOWN) { suggestions.getSelectionModel().selectNext(); event.consume(); }
                else if (event.getCode() == KeyCode.UP) { suggestions.getSelectionModel().selectPrevious(); event.consume(); }
                else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) { accept(); event.consume(); }
                else if (event.getCode() == KeyCode.ESCAPE) { popup.hide(); event.consume(); }
            }
        });
        // PopupWindow redirects owner-window key events to its content while it is open.
        suggestions.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.ENTER) { popup.hide(); query.run.fire(); event.consume(); }
            else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) { accept(); event.consume(); }
            else if (event.getCode() == KeyCode.ESCAPE) { popup.hide(); event.consume(); }
        });
        suggestions.setOnMouseClicked(event -> { if (event.getClickCount() == 2) accept(); });
        ast.getSelectionModel().selectedItemProperty().addListener((ignored, before, selected) -> {
            if (selected == null || selected.getValue() == null) return;
            var node = selected.getValue();
            query.editor.selectRange(node.start(), node.end()); query.editor.requestFocus();
        });
        invalidate();
    }
    void invalidate() {
        revision++; popup.hide(); ast.setRoot(null);
        diagnostics.setText("Analyzing SQL…");
        if (!disposed) debounce.playFromStart();
    }
    private void analyze() {
        if (disposed || pending) return;
        pending = true;
        long expected = revision; String sql = query.editor.getText(); var dialect = kind.get();
        try {
            controller.analyze(sql, dialect).whenComplete((result, failure) -> Platform.runLater(() -> {
                pending = false;
                if (disposed) return;
                if (expected != revision || dialect != kind.get()) { debounce.playFromStart(); return; }
                if (failure != null) { diagnostics.setText("SQL analysis unavailable"); return; }
                if (result.diagnostics().isEmpty()) diagnostics.setText(sql.isBlank() ? "Empty SQL" : "Parsed · Ctrl+Space for completion");
                else {
                    var error = result.diagnostics().getFirst();
                    diagnostics.setText("Line " + error.line() + ", column " + error.column() + " · " + error.message());
                }
                var root = new TreeItem<SqlAnalysis.AstNode>();
                for (var node : result.statements()) root.getChildren().add(tree(node));
                root.setExpanded(true); ast.setRoot(root);
            }));
        } catch (java.util.concurrent.RejectedExecutionException failure) {
            pending = false; diagnostics.setText("SQL analysis busy; edit to retry");
        }
    }
    private static TreeItem<SqlAnalysis.AstNode> tree(SqlAnalysis.AstNode node) {
        var item = new TreeItem<>(node);
        node.children().forEach(child -> item.getChildren().add(tree(child)));
        item.setExpanded(node.children().size() < 6); return item;
    }
    void requestCompletion() {
        if (disposed || completing || query.editor.isDisabled()) return;
        completing = true; popup.hide(); suggestions.getItems().clear(); completion = null;
        long expected = revision;
        String sql = query.editor.getText(); int caret = query.editor.getCaretPosition(); var dialect = kind.get();
        var metadata = controller.metadata();
        try {
            controller.complete(sql, caret, dialect).whenComplete((result, failure) -> Platform.runLater(() -> {
                completing = false;
                if (disposed || expected != revision || caret != query.editor.getCaretPosition() || dialect != kind.get()
                        || metadata != controller.metadata() || !query.tab.isSelected() || query.editor.getScene() == null
                        || !query.editor.getScene().getWindow().isShowing()) return;
                if (failure != null || result.items().isEmpty()) {
                    diagnostics.setText("No suggestions · expand schema / inspect relation to load metadata"); return;
                }
                completion = result; completionText = sql; completionCaret = caret; completionMetadata = metadata;
                suggestions.getItems().setAll(result.items()); suggestions.getSelectionModel().selectFirst();
                var caretNode = query.editor.lookup(".caret");
                var bounds = caretNode == null ? query.editor.localToScreen(query.editor.getBoundsInLocal())
                        : caretNode.localToScreen(caretNode.getBoundsInLocal());
                if (bounds != null) popup.show(query.editor, bounds.getMinX(), caretNode == null ? bounds.getMinY() + 32 : bounds.getMaxY());
            }));
        } catch (java.util.concurrent.RejectedExecutionException failure) { completing = false; }
    }
    private void accept() {
        var selected = suggestions.getSelectionModel().getSelectedItem();
        if (selected != null && completion != null && completionText.equals(query.editor.getText())
                && completionCaret == query.editor.getCaretPosition() && completionMetadata == controller.metadata()) {
            query.editor.replaceText(completion.start(), completion.end(), selected.insertText());
            query.editor.positionCaret(completion.start() + selected.insertText().length());
        }
        popup.hide(); query.editor.requestFocus();
    }
    void close() { disposed = true; revision++; debounce.stop(); popup.hide(); ast.setRoot(null); suggestions.getItems().clear(); }
}
