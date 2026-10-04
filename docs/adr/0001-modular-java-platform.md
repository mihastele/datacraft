# ADR 0001: Modular Java platform with a UI-independent core

- Status: Accepted
- Date: 2026-10-04
- Decision source: Explicit maintainer instruction

## Context

DataCraft is a PostgreSQL-first database IDE with eventual support for
additional databases and controlled automation. SQL analysis, metadata,
execution, and safety must remain usable independently of a desktop UI.

## Decision

Java is the primary implementation language. DataCraft is a modular Java
platform, and the desktop application is one client of its core services.

The core must compile and be testable without a desktop toolkit or visual
editor. It must not import UI classes or require a desktop event loop.
SQL intelligence must accept text and metadata through core contracts;
it must not depend on an editor widget. Query execution, cancellation,
transactions, and result streaming belong outside UI callbacks.

Database implementations sit behind adapter contracts and advertise
capabilities. PostgreSQL-specific drivers and behavior belong in the
PostgreSQL adapter, not in shared domain models or desktop business logic.
Application services receive adapters through their contracts; a client
composition root wires concrete implementations.

Safety decisions and credential-access contracts belong in the core.
Clients present policy decisions and supply explicit user authorization
where required. Credential-provider implementations may use OS facilities
without making the core depend on a desktop toolkit. This decision does
not select a secret provider or implement authorization or storage.

## Logical boundaries

These are responsibilities, not a commitment to one artifact per row.
Physical modules will be established with the build and first tested slice.

| Boundary | Responsibility |
| --- | --- |
| Domain and metadata | Driver-independent database objects and capability representations |
| Adapter API | Contracts for connectivity, introspection, execution, and supported capabilities |
| SQL intelligence | Dialect-aware parsing and analysis independent of the visual editor |
| Query execution | Asynchronous execution, cancellation, timeouts, transactions, incremental results |
| Workspace services | Local connection definitions, settings, history, credential references |
| Safety policies | Centralized analysis and policy decisions consumed by clients |
| Application services | Coordinate core workflows through these contracts |
| PostgreSQL adapter | PostgreSQL integration and vendor-specific metadata extensions |
| Desktop client | Explorer, editor, results, and interaction through application services |

Shared contracts must not depend on concrete adapters or clients. Avoid
cyclic dependencies and speculative plugin frameworks. Refine contracts
using real PostgreSQL workflows before adding other database adapters.

## Consequences

- Core tests can run without launching the desktop client.
- Later clients can reuse the core without reproducing execution or safety logic.
- Desktop toolkit choices do not define SQL or database abstractions.
- Boundary enforcement tests are required when build infrastructure exists.
- Java module system (JPMS) adoption is undecided; modular architecture does
  not by itself require JPMS.

## Decisions deferred

JDK baseline, build tool, desktop toolkit, SQL parser strategy, credential
storage provider, packaging, and license require separate decisions.
No dependencies or implementation skeletons are introduced by this ADR.
