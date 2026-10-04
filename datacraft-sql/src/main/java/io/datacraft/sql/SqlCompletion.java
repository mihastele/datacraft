/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.List;

/** Replace exactly [start,end); callers must verify document and caret revisions first. */
public record SqlCompletion(int start, int end, List<Item> items) {
    public SqlCompletion { items = List.copyOf(items); }
    public record Item(String label, String insertText, String detail) {
        @Override public String toString() { return label + "  ·  " + detail; }
    }
}
