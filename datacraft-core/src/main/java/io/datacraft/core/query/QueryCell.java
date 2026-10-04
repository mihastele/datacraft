/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

/** Null value means SQL NULL; empty text and the text "NULL" remain separate values. */
public record QueryCell(String value, boolean truncated) {
    public boolean isNull() { return value == null; }
    @Override public String toString() { return "QueryCell[value omitted]"; }
}
