# Milestone 0 implementation plan

Status: Technology choices accepted by the maintainer: Java 21, Gradle,
JavaFX, and Apache-2.0 (ADR 0002). The first deliverable is implemented;
local verification and limitations are recorded in PROJECT-STATE.md.

## First deliverable: a reproducible, tested core build

1. Record the selected JDK baseline, build tool, and license in an ADR.
2. Add a pinned build wrapper and a core module with no UI dependencies.
3. Implement immutable adapter capability declarations with explicit
   support checks. Advertise only implemented features; do not create
   adapters that pretend to connect or execute queries.
4. Test capability membership, empty declarations, and defensive copying.
   Add a build check that prevents the core from acquiring desktop or
   concrete database-driver dependencies.
5. Run the same verification command in CI and document it in README.md.

Acceptance: a clean checkout can build and test the core without launching
a desktop, connecting to a database, or providing credentials. Dependency
versions are pinned, with licenses and maintenance checked before adoption.

## Next deliverable: prove the adapter boundary against PostgreSQL

Design the smallest connection and metadata contracts needed for an actual
PostgreSQL connection and schema discovery. Propose credential handling
before implementing it. Keep driver-specific types inside the adapter.
Use an explicitly provisioned disposable test database for integration
tests, and report setup failures rather than silently skipping verification.

Acceptance: a real integration test connects, reads metadata, and closes
resources through core contracts. No hardcoded metadata is presented as
database output. Establish failure-path tests alongside the success path.

## Desktop work

Once the toolkit is selected and the core workflow works, add the desktop
client consuming application services. Database calls must run outside the
UI thread. Build the connection-to-explorer workflow before adding more
surfaces. Unsupported actions must not appear as functional controls.

## Explicitly deferred

JPMS, plugin loading, additional database adapters, SQL parser selection,
AI integrations, and production packaging are separate decisions. Query
execution and centralized safety need their own design and tests before
the desktop offers SQL execution.
