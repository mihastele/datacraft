# ADR 0006: Shared MySQL/MariaDB adapter

Status: accepted, 2026-10-04.

The maintainer requested both MySQL and MariaDB and explicitly selected one
MariaDB Connector/J driver rather than separate vendor drivers. The existing
single active connection, read-only execution, and session-only credential
scope remains unchanged.

## Architecture and dependencies

`ConnectionSettings` now includes the server `DatabaseKind`; its original
constructor still selects PostgreSQL for source compatibility. SQLite continues
to require file settings. The PostgreSQL adapter rejects other server kinds.
The immutable registry maps MYSQL and MARIADB to the same stateless
`MySqlMariaDbAdapter`. Each connection gets its own `MySqlSession`.

This extends the existing Adapter/Strategy design without putting JDBC or
vendor branching into application services. Only the desktop composition root
imports the driver adapters. The connection pane maps server inputs and picks
3306 for default MySQL/MariaDB ports. Example SQL is updated only while the
editor still contains a built-in example; custom editor text is preserved.

MariaDB Connector/J 3.5.10 was verified through official Maven/GitHub metadata
(release published July 2026) and upstream licensing. It is maintained and
officially supports both MySQL and MariaDB. One driver avoids duplicate
protocol stacks and is preferable to separate drivers or a custom connector
for this increment. LGPL-2.1-or-later permits the independently licensed
application to use the unmodified, replaceable runtime jar subject to its
redistribution terms. Upstream license/copyright notices and source/archive
links accompany the distribution; DataCraft remains Apache-2.0. All resolved
configurations are locked. No new runtime transitive library was introduced.

The standard Gradle `java-test-fixtures` plugin shares disposable server
provisioning between integration and desktop tests. Its Testcontainers
dependency and fixture jar are test-only, never product runtime dependencies.

## Connectivity and safety

Connection URLs contain only validated host/port and fixed driver options.
Credentials are passed separately. Database selection uses the driver's
structured `setCatalog`, so database punctuation cannot inject URL properties.
IPv6 hosts are bracketed when necessary. Connect, socket, network, and
statement waits are finite. Driver String credential copies remain an
unavoidable lifetime limitation; workspace-owned char arrays are wiped.

TLS defaults to the driver's `sslMode=verify-full`; plaintext is an explicit
DISABLED choice. No trust-only mode or automatic downgrade is provided.
MySQL/private CAs can use the JDK truststore. MariaDB 11.4+ and Connector/J 3.4+
also support verified zero-configuration TLS: certificate fingerprint and
password hashing authenticate the server without manually importing its
generated certificate. This requires a nonempty password. Tests verify an
encrypted MariaDB connection, wrong-password rejection, MySQL untrusted
certificate rejection, and failure against a TLS-disabled MariaDB server.
Private-CA/hostname mismatch and mutual TLS coverage remain open.

Multi-query execution, local infile uploads, and automatic RSA public-key
retrieval are disabled. Modern MySQL password authentication should use
verified TLS; plaintext authentication that requires an unpinned server RSA
key is deliberately unsupported. Only disposable plaintext MySQL fixtures
enable a legacy authentication plugin; the product never changes server
authentication settings. Test-admin key retrieval is confined to freshly
owned random-port containers and is not an application option.

The adapter issues `SET SESSION TRANSACTION READ ONLY` independently of the
JDBC read-only hint, then disables auto-commit. Every operation rolls back.
Failed rollback/network cleanup invalidates the session. A real stored
function attempts an INSERT and is rejected by both engines; tests verify the
guard table remains empty. A minimally privileged role remains recommended:
read-only mode is not a sandbox for all privileged function side effects or
temporary tables.

## Metadata and queries

Accessible databases are presented as qualified-name namespaces. The adapter
advertises only `MULTIPLE_DATABASES`, not PostgreSQL-style schemas or write
features. Parameterized `information_schema` queries inspect tables/views,
native column types (including unsigned/generated columns), ordinal positions,
and nullability. MariaDB's JSON alias is preserved as its native reported type.

Sessions add `NO_BACKSLASH_ESCAPES` to existing SQL modes, so the lexical gate
and server agree about quoted strings. Existing SQL modes are preserved.
Strings use doubled quotes; backslash escape syntax is not supported in this
read-only console. Normal block, hash and whitespace-followed dash comments
are handled. Scripts, control statements, unbound parameter/user-variable
markers, SELECT INTO, executable version comments, and optimizer hints are
rejected. This restricted lexer is separate from other dialect adapters;
broader semantic SQL parsing remains an open decision.

Server-side preparation describes original columns without executing rows or
functions. A unique CTE with ordinal aliases then projects capped UTF-8 text
and a row limit. This preserves duplicate labels and avoids shadowing normal
relation names. The shared core result builder applies the same NULL/empty,
Unicode, column/cell/row/aggregate limits as PostgreSQL and SQLite. Statement
timeouts cover execution and cancellation invokes the driver's cancel path;
the network timeout is a finite fallback. Expression evaluation can still
consume server resources before display limits apply. Typed/binary values,
bound parameters, exports, writes, older-server compatibility and arbitrary
multi-result SQL are outside this increment.

## Verification

Unit tests cover server profile kinds, dialect boundaries, fixed URL options,
capabilities and sanitized errors. Digest-pinned disposable MySQL 8.4 and
MariaDB 11.4 containers apply test migration files from scratch using
runtime-generated credentials. Every integration behavior runs against both:
catalogs/identifiers, native metadata, duplicate labels and NULLs, limits,
read-only function rejection, quoted comments/CTEs, authentication, timeout,
active cancellation, reuse, close and transaction release. Additional TLS
cases cover MariaDB verified encryption and refusal to downgrade.

Actual JavaFX workflows verify all four database choices, including connect,
metadata, result rendering, write rejection, cancellation/reuse, disconnect
and connection cleanup. Existing PostgreSQL and native SQLite tests remain
regression coverage. Other OS runs, remote CI and wider server version/auth
matrices remain unverified.

Sources: [Connector/J upstream](https://github.com/mariadb-corporation/mariadb-connector-j/tree/3.5.10),
[release](https://github.com/mariadb-corporation/mariadb-connector-j/releases/tag/3.5.10),
[TLS behavior](https://mariadb.com/docs/connectors/mariadb-connector-j/using-tls-ssl-with-mariadb-java-connector),
[MySQL read-only transactions](https://dev.mysql.com/doc/refman/8.4/en/set-transaction.html).
