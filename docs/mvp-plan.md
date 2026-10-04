# Minimal desktop MVP

Status: Recommended read-only SQL desktop scope accepted by the maintainer.
Implementation and verification are recorded in PROJECT-STATE.md and ADR 0004.
This is the original MVP scope. Saved profiles, optional Windows password storage,
and multiple query tabs now extend it; see ADR 0007.

## Recommended scope

A JavaFX desktop client for one PostgreSQL connection at a time:

- Connection form: host, port, database, username, transient password,
  environment classification, explicit TLS choice, and timeout.
- Schema and relation explorer with on-demand column inspection.
- One SQL editor with execution, cancellation, and visible status.
- A result grid with explicit NULL rendering and bounded rows/cell sizes.
- Disconnect and application shutdown that close owned resources.

The recommended query mode uses read-only database transactions. It is not
a sandbox for arbitrary SQL or a substitute for a minimally privileged
database role. The query API and policy must be designed and tested before
exposing execution controls. Controls for unimplemented features must not
appear functional.

## Boundaries

The desktop consumes application services. PostgreSQL connectivity and
query behavior remain in the adapter; query contracts and authorization
decisions remain independent of JavaFX. Only the composition root selects
concrete adapter implementations. Database calls execute off the UI thread.
Cancellation must not queue behind the query it needs to interrupt.

Connection details and query text are session-only for this MVP. No
credential persistence, history persistence, cloud account, AI, driver
plugin framework, or installer is needed to prove the workflow. The initial
deliverable runs through Gradle and includes a distribution with launcher
scripts; a bundled JDK/native installer is a later packaging decision.

## Acceptance

1. Start the application, supply a real connection, and browse real objects.
2. Select a relation and inspect columns without freezing the window.
3. For the recommended SQL scope: execute a read query, inspect bounded
   results, cancel a long query, and execute a subsequent query successfully.
4. Surface validation, connection, authentication, and query failures with
   sanitized errors; do not log passwords or raw sensitive driver messages.
5. Show the environment and query mode throughout the connected workflow.
6. Disconnect and close without leaking connections or worker threads.
7. Core tests, actual PostgreSQL integration tests, and desktop workflow
   verification pass; record any remaining verification gaps explicitly.

## Scope alternatives considered

- Recommended: read-only SQL desktop MVP including the above workflow.
- Smaller option: connection and metadata explorer only.
- Broader option: SQL desktop with writes and transaction controls, requiring
  additional centralized safety design and authorization tests.

JavaFX 21.0.12 is the selected Java 21-compatible patch line;
its license, native artifacts, and dependency locks are documented in ADR 0004.
The project has already selected JavaFX, Java 21, Gradle, and Apache-2.0.
