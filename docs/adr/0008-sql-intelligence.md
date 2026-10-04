# ADR 0008 — Local SQL AST, diagnostics and completion

Status: accepted (2026-10-04). The maintainer selected JSQLParser with multi-database completion over PostgreSQL-first ANTLR grammars.

## Decision

Add `datacraft-sql` as a UI-independent module depending on core metadata and the
exact official `com.github.jsqlparser:jsqlparser:5.4` Maven Central release.
Parser objects stay private; immutable `SqlAnalysis`, `SqlCompletion` and
`SchemaSnapshot` projections form the client API. The core retains its
java.base-only boundary. Neither adapters nor execution policies depend on
editor parsing. No JDBC, JavaFX or external services are used by the SQL engine.

Use native JavaFX TextArea, a completion popup and a collapsible TreeView, with
source-range selection, rather than adding another editor framework. Each tab
owns its editor binding and revision. A 300 ms debounce and at most one pending
analysis per tab avoid parsing every keystroke. A separate bounded analysis
worker keeps database queries independent. The parser has its own worker for
the upstream 500 ms timeout/interruption mechanism, with complex retry disabled.
Input is limited to 100000 characters; AST projection is bounded to 2000 nodes
and depth 64. Parser, workers, timers and popups close with the workspace/tab.

## Completion and metadata

A tolerant cursor lexer suppresses suggestions in comments/strings and isolates
the current statement. JSQLParser supplies actual SELECT scope/table/alias
bindings from a temporary copy in which the cursor word is replaced by an
identifier. This repaired AST is used only for completion, never shown as the
document AST or submitted for execution. The visible AST always comes from the
unchanged document; invalid documents show a sanitized advisory diagnostic.

Suggestions use immutable metadata snapshots populated by existing lazy
explorer operations. Expanding a namespace loads relations; inspecting a
relation loads columns. Refresh invalidates cached metadata, disconnect clears
it, and connection changes replace it. Completion performs no database request,
user SQL execution or row reads. Unknown metadata remains unknown.

Direct aliases and joins are resolved within the innermost parsed SELECT.
Duplicate unqualified names in multiple schemas are not resolved by guessing a
search path. CTEs and derived-table outputs are not resolved in this first slice;
CTE names are excluded from physical-table resolution to avoid false suggestions.
Outer correlated aliases, projection aliases, function signatures, semantic
diagnostics and automatic targeted metadata loading remain future work.

Suggested identifiers are safely quoted for the chosen database. Completion
edits replace the complete cursor token while preserving surrounding text.
Document revision, caret, dialect, tab and metadata identity checks reject stale
asynchronous results. Up/Down chooses, Enter/Tab inserts, Escape dismisses.
Statement syntax coverage is explicitly partial: the editor parser cannot
validate server versions, schema semantics or permissions. SQLite uses ANSI
syntax with bracket quotation; MySQL/MariaDB presets have backslash escaping
disabled to match the adapters' NO_BACKSLASH_ESCAPES setting. Other SQL modes
and vendor grammar differences can produce advisory unsupported-syntax reports.

## Dependency rationale

Checked official release metadata, September 13 2026 release notes and the
tagged upstream source/POM before adding the dependency. Version 5.4 is the
current immutable official Maven Central release. Selected its Apache-2.0
option from the LGPL-2.1/Apache-2.0 dual license; upstream notices/source links
are included with distributions and the jar is unmodified. It has no runtime
transitive libraries. Configuration versions are locked.

Compared separate ANTLR grammars, which require generated parser integration,
AST conversion and vendor-specific maintenance, and jOOQ, whose wider query
framework/transformation scope is unnecessary here. JSQLParser is established
Java infrastructure with directly traversable trees and a shared vendor grammar.
Official tagged release chosen over mutable snapshots or continuously published
build variants for a reproducible, readily reviewable baseline. No unconventional
technology introduced.

Sources: [upstream release](https://github.com/JSQLParser/JSqlParser/releases/tag/jsqlparser-5.4),
[tagged source](https://github.com/JSQLParser/JSqlParser/tree/jsqlparser-5.4),
[usage and recovery](https://jsqlparser.github.io/JSqlParser/usage.html),
[unsupported grammar](https://jsqlparser.github.io/JSqlParser/unsupported.html),
[ANTLR PostgreSQL grammar](https://github.com/antlr/grammars-v4/tree/master/sql/postgresql).

## Validation

SQL unit tests cover actual ASTs and exact source ranges, dialects, aliases,
prefix replacement, qualified relation suggestions, nested/statement isolation,
quoted identifiers, CTE/derived-table exclusion, unloaded/ambiguous metadata,
comments/strings, sanitized failures, limits and reuse. Controller tests prove
analysis completes during a blocked database query and metadata clears on close.
Real JavaFX workflows verify completion insertion, source selection, diagnostics
and stale-edit rejection against PostgreSQL, SQLite, MySQL and MariaDB. Existing
read-only execution and credential workflows remain regression checks.
