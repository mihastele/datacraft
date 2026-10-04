# ADR 0003: Driver-independent metadata sessions and PostgreSQL adapter

- Status: Accepted within the existing PostgreSQL-first architectural direction
- Date: 2026-10-04

## Scope

Establish a working connection-to-metadata workflow before a visual explorer
or query engine. The core contracts expose connectivity, liveness, schema
listing, relation discovery, column description, and session cleanup.
No query-execution, mutation, transaction-control, or secret-storage API is
implemented in this slice. Capability declarations advertise metadata
support only; database features are not advertised merely because PostgreSQL
itself supports them.

Core models preserve exact identifiers and carry relation kind, column
ordinal, display type, and nullability. Vendor type names remain display
strings, not JDBC types in shared contracts. Metadata lists are immutable
snapshots; refresh is a new targeted call. Missing column descriptions fail
explicitly, distinct from an existing zero-column relation. Schema discovery
omits system schemas and requires USAGE visibility. Relations describe
catalog metadata, not permission to read or mutate those relations.

## Adapter boundary and lifecycle

datacraft-postgresql depends on datacraft-core. JDBC and PostgreSQL classes
remain private implementation details of the adapter. The core bytecode
and runtime dependency check continues to allow only java.base.

Sessions are caller-owned, blocking, and single-owner. UI callers must run
them outside the UI thread. Metadata uses parameterized catalog queries;
identifiers are never inserted into SQL strings. Statement, socket, and
connection timeouts are finite and supplied explicitly. A read-only JDBC
transaction is rolled back after each metadata call to avoid retaining
idle transactions or stale transaction snapshots. These settings do not
constitute a production safety engine; no write API is exposed.

Clients close sessions with try-with-resources. Close is idempotent, and
later operations fail with CLOSED. Failed connection initialization closes
the acquired JDBC connection. SQL failures are mapped into generic core
categories; raw messages, SQL text, driver causes, and suppressed diagnostics
are not forwarded. This intentionally limits debugging detail until a
reviewed redaction-aware diagnostic model exists.

## Credentials and transport

ConnectionSettings has no credential field, raw JDBC URL, or arbitrary
driver properties. The caller supplies a non-null password char array at
connect time and clears it afterward. DataCraft neither modifies nor
retains that array. No persistence provider is selected or implemented.
pgJDBC requires a String copy and may retain authentication properties
internally until a connection is collected; a zero-retention claim would
be false. DataCraft does not log credentials or attach driver failures.

TLS choice is explicit: VERIFY_FULL validates the certificate and hostname;
DISABLED is a deliberate plaintext choice for controlled environments.
No opportunistic fallback or unverifiable encryption option is exposed.
Certificate/provider configuration remains driver-default behavior; a
dedicated trust configuration API is deferred. Environment classification
is metadata for future policies, not an implemented authorization gate.

## Dependencies

- pgJDBC 42.7.13: the official driver's published current release, maintained
  in 2026. BSD-2-Clause permits use alongside Apache-2.0 with attribution.
  Its license is retained in docs/licenses/pgjdbc-LICENSE. Preferred to
  alternative drivers for official maintenance, mature JDBC support, and
  PostgreSQL feature coverage. No custom network protocol is introduced.
- Testcontainers PostgreSQL 2.0.5: test-only, maintained in 2026, MIT licensed
  and compatible with Apache-2.0 with its notice preserved when distributing
  dependencies. Preferred to handwritten Docker process orchestration for
  standard readiness, dynamic port allocation, and disposable lifecycle
  management. No Testcontainers library enters the runtime artifact.
- All resolved versions are locked. Existing JUnit remains unchanged.

Sources: [pgJDBC downloads](https://jdbc.postgresql.org/download/),
[driver license](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.13/LICENSE),
[SSL behavior](https://jdbc.postgresql.org/documentation/ssl/),
[Testcontainers releases](https://github.com/testcontainers/testcontainers-java/releases),
[Testcontainers license](https://github.com/testcontainers/testcontainers-java/blob/2.0.5/LICENSE).

## Verification and gaps

Unit tests cover input validation, identifier preservation, failure
sanitization, and truthful capabilities. Integration tests provision an
isolated, digest-pinned PostgreSQL 17 container with runtime-generated
credentials. Fixture changes live in test resources/migrations and never
target an existing database. Docker failures fail the integration task;
they do not silently skip it. Default build tests are Docker-free; the
separate integrationTest task is required for adapter verification.

Positive TLS certificate/hostname tests, a PostgreSQL version matrix,
connection recovery, cancellation, metadata paging/caching, richer metadata,
secure OS storage, and query execution are separate future deliverables.
