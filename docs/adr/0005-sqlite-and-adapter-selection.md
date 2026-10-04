# ADR 0005: SQLite and explicit adapter selection

Status: accepted, 2026-10-04.

The maintainer selected SQLite as the second database, retaining one active
connection. The existing read-only PostgreSQL workflow remains available.

## Design

- `ConnectionProfile` is a sealed, immutable transport contract. The existing
  `ConnectionSettings` represents PostgreSQL server settings;
  `SqliteConnectionSettings` represents an existing local file. SQLite has no
  invented host, port, username, password, or TLS setting.
- `AdapterRegistry` holds an immutable map of database kinds to strategies.
  `WorkspaceService` resolves the adapter, owns the session, and wipes the
  password buffer even when selection or discovery fails. Only the desktop
  composition root imports concrete adapters. Unsupported registrations fail
  explicitly; no reflection, service locator singleton, or plugin framework.
- `DatabaseAdapter` / `DatabaseSession` are the Adapter contracts; PostgreSQL
  and SQLite are interchangeable execution strategies. The core depends on
  these interfaces rather than drivers (dependency inversion).
- `QueryResultBuilder` centralizes display limits, NULL handling, Unicode
  clipping, immutable snapshots, and aggregate budgeting. Each adapter still
  bounds fields before transferring them into Java; this builder is not a
  substitute for database-side limits.
- `ConnectionPane` owns connection controls and typed input mapping.
  `DesktopWindow` owns presentation coordination, `DesktopController` owns
  scheduling, and `WorkspaceService` owns connection lifetime. SQL and JDBC
  stay inside adapters. This applies single responsibility without introducing
  an inheritance hierarchy shared by unrelated database dialects.

## SQLite behavior and limits

Xerial sqlite-jdbc 3.53.4.0 bundles a native SQLite engine. Selected after
checking the publisher's current release/Maven metadata and licenses; the
August 2026 release demonstrates current maintenance. It is the established
Java JDBC implementation, preferable to custom JNI, an ORM, or a separate
SQLite process for this small adapter. Apache-2.0 and the retained permissive
Zentus notice accompany the unmodified jar. SQLite itself is public domain.
All resolved configurations are version locked. No new parser/framework or
runtime logging dependency was introduced.

Opening requires an existing regular file, URI-encodes its location, sets the
native read-only flag and `mode=ro`, disables extension loading, and enables
`query_only`. No creation, attachment, password/encryption, or write mode is
offered. Connecting validates the file through catalog discovery. SQLite
may access existing WAL/SHM files according to its native read-only behavior;
this is not an immutable/offline snapshot mode.

The explorer exposes the `main` namespace to the existing qualified-name
contract. This does not advertise PostgreSQL-style schemas: SQLite's
capabilities remain empty because no optional server-schema or mutation
feature is implemented. Tables/views, generated columns, declared types,
and SQLite-specific primary-key nullability are inspected through fixed or
parameterized catalog/pragma-table queries. Untyped columns display `ANY`.
View nullability follows available engine metadata; full expression-level
nullability inference is outside this increment.

A small SQLite-specific lexer accepts one SELECT or WITH statement, preserves
quoted identifiers/literals and comments, strips a terminal semicolon, and
rejects scripts, controls, and unbound parameter markers. The native engine
prepares the query and validates SELECT grammar through a bounded CTE wrapper.
This is a restricted lexical gate, not the broader semantic SQL parser that
remains an open decision. Native read-only mode is independent of this gate.

The adapter prepares the original SELECT for metadata without stepping its
rows, then executes a CTE projection with ordinal aliases, text substring
limits, and a row limit. A unique internal CTE name avoids shadowing ordinary
tables. Duplicate original labels are preserved. Query types are native
declared metadata and may be unknown for expressions/dynamic values. Binary
and typed inspection remain deferred. Display bounds do not prevent the
engine from allocating memory to evaluate a user's expensive expression.

Progress callbacks enforce a whole-query CPU deadline and metadata deadlines.
Cancellation sets the core token and signals native interruption; the progress
callback also checks the token. Query lock waits are capped at one second
(or the shorter request timeout), so lock contention may time out before the
whole-query deadline. Query cleanup detaches callbacks, restores the metadata
busy timeout, and rolls back. A failed rollback invalidates the session.
Errors omit raw SQL, paths, and driver causes.

## Verification

Core tests cover registry routing/immutability, unsupported kinds and buffer
wiping, typed file settings, shared cell/row/column/aggregate limits, and
Unicode. Native SQLite tests create only temporary files from migration
fixtures and cover discovery, identifiers, key nullability, actual results,
limits, missing/invalid files, rejected writes/scripts/extension loading,
byte-for-byte file preservation, timeout/cancellation recovery, and handle
release on Windows. These run in the ordinary Docker-free build.

PostgreSQL integration tests remain regression coverage for the shared result
refactor. JavaFX workflows exercise both real PostgreSQL and a temporary
SQLite file, including switching after disconnect, metadata, query, rejected
write, cancellation, reuse, and cleanup. Remote CI / other-platform native
startup, SQLite version compatibility and encrypted files remain unverified.
