/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.List;

/** Immutable editor-neutral projection. No vendor AST classes escape this module. */
public record SqlAnalysis(List<AstNode> statements, List<Diagnostic> diagnostics) {
    public SqlAnalysis { statements = List.copyOf(statements); diagnostics = List.copyOf(diagnostics); }
    public record AstNode(String kind, int start, int end, List<AstNode> children) {
        public AstNode { children = List.copyOf(children); }
        @Override public String toString() { return kind + " [" + start + ", " + end + ")"; }
    }
    public record Diagnostic(int offset, int line, int column, String message) {}
}
