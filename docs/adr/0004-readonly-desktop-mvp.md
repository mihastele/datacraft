# ADR 0004: Session-only read-only PostgreSQL desktop MVP

- Status: Accepted
- Date: 2026-10-04
- Decision source: Maintainer accepted the recommended minimal MVP scope

## User workflow

One transient connection, PostgreSQL schema/relation explorer, column
inspection, one SQL editor, bounded text results, cancellation, and
disconnect. The selected text is executed when present; otherwise the
whole editor is used. Ctrl+Enter runs the query. No persistence or
write/transaction controls are offered.

DesktopWindow contains presentation and interaction only. DataCraftApp is
the composition root selecting PostgreSqlAdapter. WorkspaceService owns
the session lifecycle. DesktopController runs services on one serialized
worker and sends cancellation from a separate worker. Neither the core
contracts nor the controller require JavaFX or JDBC types.

## Query safety

The adapter reuses the pinned pgJDBC Parser for statement splitting and
command classification. ReadOnlyQueryPolicy centrally requires exactly one
SELECT, including a WITH whose main command is SELECT. Scripts, SET,
transaction controls, DDL, and direct DML are blocked. The tiny comment-only
fragment check accommodates pgJDBC's trailing trivia without treating
unknown commands as empty. standard_conforming_strings is enabled when
connecting so classification and server parsing agree.

The session remains in PostgreSQL read-only transactions, and each operation
rolls back. Tests show that function writes are rejected and that writable
CTEs cannot execute through the result wrapper. The wrapper does not support
all PostgreSQL statements; unsupported features are reported as policy
failures, rather than pretending to execute. This is not a full SQL parser,
refactoring engine, or sandbox: privileged functions may have effects outside
ordinary database writes. Use a role with only the privileges needed for
read work. Environment labels remain prominent; no production-specific
write policy is needed because the MVP offers no write mode.

Driver-internal Parser APIs are version-coupled. Keep dialect boundary tests
when upgrading pgJDBC. Reusing its splitter is preferred to a handwritten
SQL lexer or a new unselected parser dependency at this stage.

## Bounds and results

A zero-row wrapper obtains original result metadata. A second wrapper uses
generated ordinal aliases and projects bounded text values on the server.
This preserves duplicate labels and avoids receiving entire oversized
display cells. Both wrappers use the same read-only transaction. Metadata
and query planning can run twice; this implementation targets ordinary
SELECT workflows rather than transparent arbitrary-SQL semantics.

Limits are 100000 SQL characters, 1000 rows (default 500), 128 columns,
4096 UTF-16 units per displayed cell, and 2000000 retained value units
across all rows. Surrogate pairs are not split when clipping. NULL,
empty text, and literal NULL text remain distinct. Row/cell omissions are
reported as truncation. Values are text displays, not editable typed objects.
Database execution and casting can still consume server resources; finite
timeouts do not constitute server resource quotas.

The JDBC cursor uses forward-only results, finite fetch size, and disabled
autocommit. Extra rows identify truncation without materializing the entire
result. Callers receive immutable bounded snapshots rather than live cursors.
There is no pagination or full-result export in this MVP.

Cancellation is one-shot and best-effort through Statement.cancel, with
pre/post cancellation checks and finite query/network timeouts as a fallback.
A per-request timeout budget is applied across the two query phases (rounded
to whole seconds by JDBC). Rollback allows another query after cancellation,
syntax errors, or timeout. Failed rollback closes the session rather than
leaving an unknown usable transaction. Cancellation callbacks are detached
before later queries; shutdown requests cancellation and queues cleanup.

## Credentials and errors

WorkspaceService consumes and wipes the caller's array on every connection
exit path, including failed introspection. The PasswordField is cleared
immediately on submission or input failure. JavaFX text controls and the
driver necessarily use String objects; this is transient UI use, not a
claim of zero retention. No workspace files or query history are written.
SQL/value toString methods omit sensitive content. Error UI uses fixed
messages for generic failure kinds and never displays raw driver messages.

## Desktop dependency and distribution

OpenJFX base/graphics/controls 21.0.12 are pinned, with OS-specific native
classifiers and generated dependency locks. The maintained Java 21 patch
line is preferred to an additional desktop toolkit or a plugin dependency.
Its GPLv2 with Classpath Exception permits the independent Apache-2.0
application to link to it. Retain upstream license and native third-party
notices; do not copy GPL source into DataCraft source. Source links and
attributions live in docs/licenses. No JavaFX binaries are modified.

Gradle produces a platform-specific application distribution with launcher
scripts and library jars, requiring an installed JDK 21. No native installer
or bundled runtime is selected. The launcher also accepts --verify-launch
for a startup/shutdown smoke check without database access. JPMS remains
deferred; the non-modular startup may emit JavaFX's unnamed-module warning.

## Verification

Unit tests cover policy, immutable bounded results, cancellation registration,
credential consumption, failed-connect cleanup, and worker separation.
Real PostgreSQL tests cover writes, scripts, quote/comment/dollar-string
boundaries, large values/row limits, display budget, column limits, Unicode,
duplicate labels, timeout, cancellation, and subsequent session reuse.
The JavaFX workflow test drives real controls against its own PostgreSQL
container, verifies responsive cancellation and disconnect cleanup, and
saves a rendered screenshot for visual review. No demo objects appear in
the application; static test data lives only in disposable fixtures.

Future work: version-matrix and positive TLS tests, pagination, typed/binary
inspection, completion, multiple tabs, secure persistence, and production
installers. They are outside the accepted minimal MVP.
