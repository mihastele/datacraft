# Project State

## Current Facts

| Item | Value | Set on |
| --- | --- | --- |
| Project | DataCraft | 2026-10-04 |
| Workspace | D:\development\opensource\datacraft | 2026-10-04 |
| Repository | Workspace is not yet a Git repository; remote not configured | 2026-10-04 |
| Milestone | Read-only PostgreSQL, SQLite, MySQL and MariaDB desktop workflows complete; broader foundation / Milestone 1 preview still in progress | 2026-10-04 |
| Primary implementation language | Java | 2026-10-04 |
| Architecture | Modular Java platform; desktop is one client of a UI-independent core | 2026-10-04 |
| Initial database target | PostgreSQL | 2026-10-04 |
| Product brief | MISSION.md, preserved from maintainer attachment | 2026-10-04 |
| License | Apache-2.0 | 2026-10-04 |
| JDK baseline | Java 21; no preview features | 2026-10-04 |
| Build tool | Gradle 9.3.1; checksum-pinned wrapper | 2026-10-04 |
| Desktop toolkit | OpenJFX 21.0.12 base/graphics/controls; functional JavaFX desktop client | 2026-10-04 |
| Desktop visual theme | Light slate workspace, navy brand header, teal actions, centralized JavaFX CSS; minimum-window layout checked | 2026-10-04 |
| Implementation / tests / CI | Four database choices + JavaFX on UI-independent core; 36 unit, 13 native SQLite, 39 server integration, 4 real desktop workflows (92 total); CI configured, not run remotely | 2026-10-04 |
| PostgreSQL driver | pgJDBC 42.7.13, isolated in datacraft-postgresql | 2026-10-04 |
| SQLite driver | Xerial sqlite-jdbc 3.53.4.0, isolated in datacraft-sqlite; existing files only, native read-only mode | 2026-10-04 |
| MySQL/MariaDB driver | MariaDB Connector/J 3.5.10, shared datacraft-mysql adapter; default verified TLS and server-enforced read-only transactions | 2026-10-04 |
| Database selection | PostgreSQL, SQLite, MySQL or MariaDB; one transient active connection; disconnect before switching | 2026-10-04 |
| Integration infrastructure | Testcontainers 2.0.5, digest-pinned disposable PostgreSQL 17, MySQL 8.4 and MariaDB 11.4 | 2026-10-04 |
| Secrets location | Caller-owned arrays at connect time; no credential persistence; test credentials generated at runtime | 2026-10-04 |
| MVP distribution | Gradle application ZIP/TAR and installDist launchers; host-specific native jars, installed JDK 21 required | 2026-10-04 |
| MVP launch command | .\datacraft-desktop\build\install\datacraft\bin\datacraft.bat or .\gradlew.bat :datacraft-desktop:run | 2026-10-04 |

## Open decisions

- Choose bundled-runtime/native-installer packaging before a production desktop release. MVP uses Gradle application distributions with an installed JDK 21.
- Choose SQL parser strategy and secure credential-provider implementations through separate ADRs.
- Determine whether JPMS adds value beyond build-module boundaries.

## Open items

- Add server version/authentication matrices and broader cross-platform desktop workflows. All four database workflows pass on local Windows; Linux/Xvfb CI is configured but unverified remotely. Other product-principle coverage remains open.
- Add PostgreSQL/MySQL positive private-CA and hostname mismatch TLS tests. MariaDB verified zero-configuration encryption, wrong-password rejection, and TLS non-downgrade now pass; mutual TLS and additional truststore configurations remain open.
- Extend the implemented read-only query pipeline: typed/binary inspection, parameterized queries, pagination/export, multiple result sets/tabs, richer metadata, and paging/cache invalidation. Write mode and production mutation policies are separate future decisions.
- Establish logging/redaction infrastructure and richer safe diagnostics; current adapter errors intentionally omit raw driver details.
- Run the CI workflow on a configured GitHub repository; only local Windows verification has run so far.
- Adapt AGENTS.md template to DataCraft: product constraints, database-client applicability of server rules, and applicable conditional sections. MISSION.md supplies the concrete product principles meanwhile.
- Establish Git repository and remote when requested.
- Extend SQLite compatibility coverage beyond the bundled engine/local Windows workflow, including WAL/SHM and lock-contention scenarios. Attached/encrypted databases and typed/binary inspection are outside the implemented file workflow.

## Log

### 2026-10-04 — Milestone 0: Java architectural direction

- Initial inspection found only AGENTS.md; no previous project-state file or Git repository existed. No previous milestone or blockers could be recovered.
- Preserved the supplied DataCraft brief as MISSION.md.
- Recorded the maintainer's explicit choice of Java and a modular platform with a UI-independent core in ADR 0001.
- Documented logical responsibilities and dependency constraints without choosing a toolkit, introducing dependencies, or creating disconnected implementation skeletons.
- Added README.md with the current project status and document links.
- Validation: checked the brief copy against the attachment and verified local Markdown links in the new documents. No executable tests exist; establishing the test suite remains an explicit open item.
- No implementation TODOs, database operations, credential storage changes, or departures from boring technology introduced.
- STOPPED — next: resolve JDK baseline, build tool, and license, then establish the first tested foundation slice; select desktop toolkit before desktop implementation.

### 2026-10-04 — Milestone 0: implementation preparation

- Reviewed the full project state and accepted Java architecture before proceeding.
- Verified local tools: Amazon Corretto JDK 21.0.10 and Gradle 9.3.1 are installed; Git and Docker commands are available. Docker engine availability was not tested.
- Presented JDK/build, desktop, and license choices to the maintainer; no choice has been accepted by default.
- Added docs/foundation-plan.md with a concrete first deliverable, meaningful capability tests, architectural dependency checks, CI, and a subsequent real PostgreSQL integration slice.
- Checked official Java, Gradle, JavaFX, and licensing documentation while preparing options. No dependencies were installed or selected.
- Validation: verified relative document links. No code was changed and no executable test suite exists; that gap remains an open item.
- STOPPED — implementation awaits the maintainer's technology decisions under AGENTS.md's ambiguity rule. No broken or partially migrated build exists.

### 2026-10-04 — Milestone 0: first tested Java core foundation

- Maintainer selected Java 21, Gradle, JavaFX, and Apache-2.0. Recorded the choices and dependency rationale in ADR 0002; added the official Apache-2.0 LICENSE text.
- Established a Gradle 9.3.1 wrapper using the official distribution SHA-256 and separately verified the wrapper JAR against its official checksum. Used the installed stable patch release instead of introducing a new build-tool installation.
- Added datacraft-core with immutable capability declarations and explicit support checks. It has no runtime dependencies, no fake adapter, and no desktop/database operations.
- Added JUnit Jupiter and platform launcher 6.0.3 as test-only dependencies, with exact versions and generated transitive lockfile. Checked the project's release notes and EPL-2.0 license; chose JUnit over TestNG/custom runners for familiar Gradle integration. JUnit is not included in the core JAR. No unusual technology introduced.
- Added bytecode boundary verification with JDK jdeps plus a runtime-classpath check. At this stage the core can depend only on java.base; client/driver dependencies are rejected.
- Added GitHub Actions Windows/Linux builds with checkout and setup-java actions pinned to verified v4 commit SHAs. CI is configured but has not run remotely; no Git repository/remote exists yet.
- Updated README build/test instructions, lockfile update instructions, local troubleshooting, project status, and license. Ignored build outputs and ordinary local environment files.
- Validation: wrapper clean build passed using the generated lockfile; all 6 JUnit tests passed with zero failures/errors/skips. A temporary compiled java.sql dependency caused the boundary check to fail as intended; removed the probe and reran clean build successfully. Verified Markdown links and wrapper checksum.
- Open gaps: real database integration, other product-principle tests, desktop client, and remote CI execution remain unimplemented. JavaFX is selected but its dependency/version will be added with actual desktop work. No implementation TODOs or success-returning stubs were introduced.
- STOPPED — next: design the smallest real PostgreSQL connection/metadata workflow and propose credential handling before implementing it. Milestone 0 overall is still in progress.

### FAILED — foundation verification issues resolved in this session

- Initial Gradle startup failed on this Windows Corretto installation with `Unable to establish loopback connection`, caused by `Invalid argument: connect` in the JDK Unix-domain socket path. IPv4/selector-provider retries did not resolve it. Setting `jdk.net.unixdomain.tmpdir` to the workspace's existing `.gradle/socket-tmp` directory resolved startup. Documented the shell-local workaround in README; no global JDK/system settings changed. Repro: run Gradle with the default local socket temp configuration.
- Initial boundary task used unsupported `jdeps --summary`. Replaced it with the supported `-s` option, verified actual rejection behavior, and completed a successful clean build. No broken build remains.

### 2026-10-04 — Milestone 0: real PostgreSQL connection and metadata core

- Reviewed project state and existing boundaries. Confirmed Docker engine availability; all integration operations targeted freshly provisioned test containers, never an existing database.
- Proposed ephemeral credential handling and metadata-only session scope before implementing. Kept OS credential persistence deferred; no credential is embedded in settings, source, logs, or this state file. JDBC requires an internal String copy, documented as a retention limitation in ADR 0003.
- Added driver-independent connection settings, environment/TLS classification, qualified names, relation/column metadata, DatabaseAdapter and caller-owned DatabaseSession contracts, and sanitized DatabaseException categories. Operations are explicitly blocking/single-owner and offer no arbitrary SQL or mutation API.
- Implemented datacraft-postgresql: bounded connection/socket/statement timeouts, explicit verified TLS or plaintext choice, read-only metadata transactions, parameterized schema/relation/column queries, immutable snapshots, liveness, transaction release after metadata reads, and idempotent close. Exact quoted identifiers, dropped-column ordinal gaps, domain nullability, views, materialized views, and partitioned tables are preserved.
- Added pgJDBC 42.7.13 after checking official release availability/maintenance and BSD-2-Clause licensing; preferred the official mature JDBC implementation to alternative/custom drivers. Retained its license notice in docs/licenses. Its checker-qual 3.55.1 runtime annotation dependency declares MIT licensing in the publisher POM. All resolved runtime/test configurations have generated lockfiles.
- Added test-only Testcontainers PostgreSQL 2.0.5 after checking maintenance/release availability and MIT licensing, compatible with the project's Apache-2.0 distribution with notice obligations. Used standard container lifecycle/readiness instead of custom Docker orchestration. Runtime-generated test credentials are never written to repository files.
- Test schema setup is migrations/001_metadata_fixture.sql inside test resources, applied from scratch only in a new disposable database. No application-owned persistent schema/user-content table or live database migration was introduced; fixture tables contain no user data. Core types are hand-defined adapter-independent metadata, not generated application-schema types.
- Added 9 unit tests (6 core and 3 adapter), plus 6 real PostgreSQL integration tests. Tests cover settings/identifier validation, error sanitization, honest capabilities, actual catalog discovery/types, missing versus empty objects, parameter injection resistance, closed sessions, authentication failures/caller-array ownership, TLS non-downgrade, idle transaction prevention, and session cleanup.
- Validation: clean build and explicit integrationTest passed with existing lockfiles: 21 tests total, zero failures/errors/skips. Core java.base-only boundary check still passes. Resolved and locked all configurations, including adapter runtime dependencies. Verified documentation links and absence of implementation TODO/FIXME markers.
- Updated README, ADR 0003, and Linux CI integration step. Default build remains Docker-free; integrationTest explicitly fails if Docker/setup fails. Remote CI and positive TLS verification are recorded open gaps, not claimed complete.
- No nonstandard technology or placeholder-success implementations introduced. Milestone 0 remains in progress.
- STOPPED — next: design query execution with cancellation, timeout behavior, and bounded streaming alongside centralized safety; secure storage and SQL parsing remain open decisions.

### FAILED — PostgreSQL integration setup corrected in this session

- First integration run failed before database provisioning: Testcontainers rejected the combined tag-and-digest image reference. Repro was the original postgres:17@sha256 reference in the test fixture. Switched to the official image's digest-only reference; actual PostgreSQL integration tests then passed, including a subsequent clean build. No compatibility bypass or silent test skip was added.

### 2026-10-04 — MVP scope preparation

- Maintainer requested a minimal MVP. Reviewed the complete project state, core contracts, README, and foundation plan.
- Proposed a JavaFX desktop with one transient connection, metadata explorer, one SQL editor, bounded results, cancellation, and disconnect in read-only query mode. Asked whether to choose that scope, metadata-only browsing, or write-enabled SQL; no scope choice has been accepted implicitly.
- Added docs/mvp-plan.md with concrete user workflow, architectural boundaries, acceptance criteria, deferred features, and the requested scope alternatives.
- Verified availability of JavaFX 21.0.12 through publisher Maven metadata and inspected official JavaFX documentation/license sources. No JavaFX dependency or query/safety implementation added yet.
- Validation: verified local document references. No executable code changed, so the last verified 21-test result remains the baseline; the desktop/query verification gap remains explicitly open.
- STOPPED — next: resolve the MVP scope selection under AGENTS.md's ambiguity rule, then implement the complete chosen desktop workflow. Existing core and adapter remain unchanged and buildable.

### 2026-10-04 — Minimal read-only PostgreSQL desktop MVP completed

- Maintainer accepted the recommended scope with “Sure”: transient connection form, metadata explorer, one SQL editor, bounded results, cancellation, and disconnect. Implemented the chosen workflow rather than the metadata-only or write-enabled alternatives. Recorded design and limitations in ADR 0004.
- Added UI-independent query request/result/cancellation contracts, a centralized single-SELECT policy, and WorkspaceService session ownership. WorkspaceService consumes and wipes credential arrays on every connect exit path and closes partial connections on failed discovery. Query/value toString output omits sensitive text.
- PostgreSQL execution reuses the pinned driver's statement splitter/classification instead of introducing a new parser framework. Added only a small nested-comment trivia filter to accommodate driver split output. This is a deliberately limited classification gate, not SQL semantic analysis; version-coupled parser usage is documented and covered by real dialect boundary tests.
- Queries run in read-only transactions, with rollback after each request, finite timeout/cancellation paths, and session invalidation if rollback fails. DDL, DML, scripts, and transaction controls are blocked. Real tests verify read-only function-write rejection, writable CTE rejection, quoted/dollar-string/comment boundaries, and session reuse after errors, timeout, and cancellation. The client is not an arbitrary-SQL sandbox; README instructs use of a minimally privileged database role.
- Bounded result wrappers obtain metadata without rows and project capped text on the server, avoiding full oversized cell transfer. Forward-only cursor fetching and row/column/cell/aggregate display limits prevent unbounded accumulation. NULL/empty/literal NULL, truncation, Unicode surrogate pairs, and duplicate labels are preserved. Limits and the two-phase query semantics are explicitly documented; no full-result export or typed editing is claimed.
- Added datacraft-desktop: connection form with explicit TLS/environment, lazy real-object explorer, columns inspector, single editor, run-selection / Ctrl+Enter, virtualized result table, fixed sanitized errors, separate cancellation worker, and disconnect/window-close cleanup. Database business logic stays in core/adapter services; only DataCraftApp's composition root selects the PostgreSQL adapter. No fake database objects or nonfunctional controls introduced.
- Added OpenJFX 21.0.12 native-classifier dependencies after checking publisher artifacts and maintained upstream tags. GPLv2 with Classpath Exception permits linking the independent Apache-2.0 application; retained upstream license, Classpath explanation, native-library notices, source links, and the MIT checker-qual notice. Chose direct three-module dependencies over an extra Gradle plugin. Versions/configurations are locked; no unusual technology introduced.
- Added 8 core unit tests, 1 desktop controller test, 8 additional real adapter tests, and 1 end-to-end JavaFX test using its own runtime-credential PostgreSQL container. New schema fixtures are migration files under test resources and apply only to disposable databases, with no user content or live schema changes.
- Validation: clean build + integrationTest + desktopTest + installDist passed: 39 tests total, zero failures/errors/skips. Core java.base-only boundary check passes. Desktop test drives actual connect/explorer/columns/query/NULL/cancel/Ctrl+Enter/reuse/disconnect controls, checks no leaked client connections, and produces a visually inspected screenshot. Installed Windows launcher startup/shutdown check passed without the Gradle socket workaround. Native JavaFX classpath warning is documented.
- Generated host-specific distribution ZIP/TAR and installed launcher scripts; they require JDK 21 and include runtime libraries/notices. Added Linux/Xvfb desktop CI step and updated README run/test instructions. Remote CI and other-platform startup are not claimed verified.
- No implementation TODO/FIXME markers remain. Logging/redaction expansion, positive TLS and version-matrix tests, secure persistence, completion, multiple tabs, write controls, exports, and native installers remain explicit open items; the full Milestone 1 developer preview is not declared complete.
- STOPPED — requested minimal MVP is complete. Next: gather hands-on feedback, then select the next small workflow improvement.

### FAILED — MVP verification issues corrected in this session

- Initial query integration suite rejected a harmless trailing comment as a second statement and reported writable-CTE wrapper rejection as OTHER. Inspected the real driver's split output, added comment-only normalization, and categorized PostgreSQL read-only/unsupported-wrapper failures as POLICY. Reran the full suite successfully; direct writes, scripts, function writes, and transaction controls remain blocked.
- First desktop compile used TreeView.setPlaceholder, which does not exist in JavaFX. Removed the invalid call; the explorer starts empty with the workspace status explaining connection setup. Subsequent clean compile, real UI workflow, and launcher checks passed. No broken build remains.

### 2026-10-04 — Multi-database increment: SQLite and clean adapter selection

- Reviewed the complete project-state record and existing adapter boundaries. Maintainer explicitly chose SQLite as the second database while keeping one active connection, resolving the materially different vendor/concurrency directions required by AGENTS.md.
- Added immutable typed ConnectionProfile settings for server versus file transports, DatabaseKind, and an explicit immutable AdapterRegistry. WorkspaceService selects the strategy through core contracts and continues to own session lifetime, partial-connect cleanup, and password wiping on failure. Concrete drivers are selected only in the desktop composition root; the core remains java.base-only with no runtime libraries.
- Implemented datacraft-sqlite: existing-file validation and URI encoding, native read-only flags plus mode=ro/query_only, disabled extension loading, actual table/view discovery, generated columns, declared types, safe parameterized identifiers, sanitized errors, metadata deadlines, bounded SELECT execution, native/progress cancellation, timeout recovery, transaction release, and idempotent cleanup. SQLite's main namespace does not falsely advertise server-schema capabilities.
- Preserved SQLite-specific primary-key nullability: INTEGER rowid aliases, STRICT and WITHOUT ROWID keys differ from ordinary nullable text/descending keys. Added a real fixture/test for these cases instead of assuming PostgreSQL semantics.
- Extracted QueryResultBuilder so both adapters share column/row/cell/aggregate display limits, NULL/empty handling, immutable snapshots, and surrogate-safe clipping. Database-side projections still bound transferred fields. Kept SQL grammar/wrappers vendor-specific rather than adding a common JDBC superclass or parser framework.
- Extracted ConnectionPane from DesktopWindow, with database selection, appropriate server/file fields, Browse, typed input mapping, and session-only credential clearing. Controller scheduling and core session ownership remain separate responsibilities. Default sample SQL follows the selected database without overwriting user-edited SQL. Kept an explicit usable editor minimum height after visual review.
- Chose Xerial sqlite-jdbc 3.53.4.0 over custom JNI, an ORM, or a subprocess after checking official GitHub/Maven release metadata and the August 2026 maintained release. Its Apache-2.0 and permissive Zentus notices are retained with the unmodified bundled SQLite engine; updated runtime notices/source links and regenerated all SQLite/desktop configuration locks. No new parser, runtime logging package, unconventional technology, credential persistence, or live database mutation introduced.
- New native and desktop schemas are migration fixtures applied from scratch only to freshly created temporary test files. They contain no user data; DataCraft owns no persistent application-content schema. Tests never access or modify an existing user database.
- Validation: clean build + PostgreSQL integrationTest + both real JavaFX workflows + installDist passed. Final relevant rerun after SQLite metadata refinement and UI layout adjustment also passed. All 59 tests pass with zero failures/errors/skips: 30 unit, 13 native SQLite, 14 PostgreSQL integration, 2 desktop workflows. Core boundary verification still passes. Both screenshots were inspected; checked document links and absence of TODO/FIXME markers. Distribution archives contain both adapters, the exact SQLite driver, and runtime notices, without test libraries. Installed Windows launcher startup/shutdown passed without the Gradle socket workaround; the existing JavaFX unnamed-module warning remains documented.
- Native tests cover identifiers/injection, missing/invalid files and no creation, caller credential ownership, exact NULL/empty/duplicate-label results, Unicode and display budgets, rejected scripts/writes/extension loading, unchanged fixture bytes, CPU timeout/cancellation recovery, closed-session behavior, and native file-handle release. JavaFX now verifies switching from PostgreSQL to SQLite after disconnect, real explorer/columns/query results, rejected writes, cancel/reuse, and disconnect cleanup.
- Updated README, ADR 0005, license notices, distribution contents, and CI workflow description. Other-platform native startup, remote CI, SQLite compatibility/WAL/SHM/lock-contention expansion, multiple active connections, encrypted/attached files, parameter binding, write mode, and broader SQL semantic analysis remain explicit limits/open work. No task remains BLOCKED and no implementation TODO was introduced. Git is still uninitialized; no commit or remote CI run is claimed.
- STOPPED — requested SQLite multi-database increment is complete. Next: choose the next workflow improvement (such as result export/pagination or richer metadata) while preserving the read-only/session-only scope.

### 2026-10-04 — MySQL/MariaDB read-only desktop support completed

- Reviewed the complete state and repository rules, reported the previous SQLite milestone, and kept the accepted single-active-connection/read-only/session-only scope. Maintainer explicitly selected one MariaDB Connector/J driver for both server families rather than separate drivers.
- Extended typed server ConnectionSettings with DatabaseKind while preserving the original PostgreSQL constructor. SQLite still requires file settings; the PostgreSQL adapter now rejects other server kinds. Registered a stateless shared MySqlMariaDbAdapter with separate per-connection sessions. Core remains UI/driver independent and java.base-only.
- Added datacraft-mysql with real connectivity, parameterized database/table/view/column metadata, unsigned/generated/native type handling, bounded SELECT/CTE text results, original duplicate labels, cancellation, finite timeouts, rollback recovery, sanitized errors and close. Server database namespaces advertise only the actually implemented MULTIPLE_DATABASES capability.
- Used validated host/port and fixed driver options with structured setCatalog database selection; database names and credentials cannot inject connection URL properties. Default TLS is verify-full; disabled multi-queries/local infile/public-key retrieval. Enforced SET SESSION TRANSACTION READ ONLY independently of the JDBC hint. Native tests prove a stored function attempting INSERT is rejected by both engines and leaves the guard table empty.
- Pinned the session to NO_BACKSLASH_ESCAPES while preserving existing SQL modes, and added a MySQL-specific lexical policy with native quote/hash/dash/block comment handling. Rejects scripts/controls, executable version comments, hints, user-variable/parameter markers and SELECT INTO. Kept the bounded CTE wrapper and dialect policy inside the adapter; reused the existing shared result builder rather than inventing a common SQL abstraction or JDBC superclass.
- Added MySQL and MariaDB desktop choices, default port 3306, appropriate server fields/example SQL, and a generic application title. Custom ports and edited SQL remain intact. Only the composition root selects concrete adapters; UI/controller/service responsibilities remain separated.
- Verified maintained MariaDB Connector/J 3.5.10 through official Maven/GitHub metadata (July 2026 release) and LGPL-2.1-or-later licensing. Chose it for official support of both databases and fewer runtime libraries than separate drivers/custom protocol code. Included unmodified library license/copyright notices and source/archive links, documented replacement/debugging rights, and generated all module/desktop configuration lockfiles. No new runtime transitive dependency or unusual technology introduced.
- Used standard Gradle java-test-fixtures to share disposable Testcontainers provisioning between adapter and JavaFX suites. Digest-pinned MySQL 8.4/MariaDB 11.4 containers use runtime-generated credentials and test migration files applied from scratch only to new owned containers. No existing database, application-owned user schema, server user/auth configuration, credential persistence or production mutation was touched. Native-password enablement and admin RSA-key retrieval are explicitly confined to disposable plaintext test fixtures, never product settings.
- Validation: clean build + integrationTest + all four desktop workflows + installDist passed, with 92 tests and zero failures/errors/skips: 36 unit, 13 native SQLite, 14 PostgreSQL integration, 25 MySQL/MariaDB integration, 4 real JavaFX workflows. Reran the MySQL/MariaDB suite after final test credential cleanup and refreshed archives. Core boundary check passed, document links/TODO checks passed, MySQL/MariaDB screenshots were visually inspected. Distribution includes the shared adapter/driver/notices and excludes test-fixture, JUnit and Testcontainers jars. Installed launcher startup/shutdown passed; the known JavaFX classpath warning remains documented.
- Tests cover both server families for catalogs/injection/structured database selection, native metadata, duplicates/NULL/empty text, Unicode/row/cell/aggregate/column bounds, comments/CTEs/relation-name collisions, rejected writes/function writes/scripts/file output, authentication, timeout, confirmed active cancellation/reuse, transaction release and close. MariaDB verified zero-configuration TLS succeeds with a nonempty password and reports an SSL cipher; a TLS-disabled server cannot trigger plaintext fallback. MySQL rejects an untrusted generated certificate. Each new JavaFX workflow also checks actual metadata/results, write rejection, cancel/reuse, password clearing and no remaining client connections after disconnect.
- Updated README, ADR 0006, licensing, CI descriptions and current facts. Native query compatibility is tested against MySQL 8.4 and MariaDB 11.4; wider versions/auth plugins, MySQL/PostgreSQL private-CA/hostname matrices, other platforms and remote CI remain open. Broader SQL analysis, writes, bound parameters, exports, persistent credentials and concurrent connections remain outside this increment. Git remains uninitialized; no commit or remote CI run claimed. No unresolved TODO or BLOCKED work remains.
- STOPPED — requested MySQL/MariaDB increment is complete; four database choices are available in the read-only desktop.

### FAILED — MySQL/MariaDB verification issues resolved in this session

- The first integration run passed 23/24 cases but incorrectly expected MariaDB VERIFY_FULL to reject its generated certificate. Repro: authenticationTlsAndCallerOwnershipAreExplicit(MARIADB) expected DatabaseException, but a verified connection succeeded. Checked upstream zero-configuration TLS behavior (MariaDB 11.4+, Connector/J 3.4+ verifies certificate fingerprint using password hashing). Corrected the test to close the successful session and assert actual encryption/wrong-password rejection; added a separate TLS-disabled server rejection test. Final 25-case adapter suite and full build/workflows pass; no trust-only setting or downgrade was introduced.
- A documentation/test-cleanup script initially used Python's Windows cp1250 default to read a UTF-8 Java test containing emoji, producing UnicodeDecodeError before writing any file. Repro: Path.read_text() without encoding on MySqlIntegrationTest.java. Reran with explicit UTF-8; preserved test SQL/Unicode and successfully recompiled/reran the relevant suite. No partial migration or broken build remains.


### 2026-10-04 — Desktop visual refinement

- Continued from the completed four-database read-only workflows (92-test baseline). No BLOCKED work or new library/UX direction decision was required; retained the existing light workspace, single connection, and connection/explorer/query/columns flow.
- Added workspace.css as the centralized theme resource: navy brand header and native D mark, teal primary actions, light slate background, rounded connection/sidebar panels, consistent field labels, spacing, focus/hover/disabled states, readable SQL typography, alternating result rows, and distinct SQL NULL styling. Used standard JavaFX controls/CSS and system fonts; added no dependencies, external assets, unusual technology, or persisted data.
- Refined the connection form with labeled options and proportional server fields, and gave the explorer native object symbols and an informative disconnected state. Kept read-only/environment badges explicit, including a separate production color. Kept metadata and real result counts/timing/truncation visible; query failure/cancellation now clears the transient Running summary.
- Kept presentation rules separate from UI-independent services and adapters. CSS pseudo classes express connection/busy/production/NULL states, including resetting recycled table cells. ConnectionPane still handles presentation/input mapping; application services still own sessions and database operations.
- Added disconnected and minimum-window screenshots to the existing actual JavaFX workflow suite, and changed input visibility checks to inspect visible ancestors rather than depend on a particular field wrapper. Awaited actual scene resizing before capturing the minimum window. Visual review caught cramped editor/results sizing and truncated TLS text; reduced editor minimum, retained a visible result pane, widened TLS, and used a compact result placeholder. Reviewed connected PostgreSQL/SQLite/MySQL/MariaDB layouts, disconnected layout, and the 980x650 outer-window layout (964x611 client area).
- Validation: final Gradle build + all four desktopTest workflows + installDist passed with zero failures/errors/skips. The build checks 49 non-container tests (36 unit + 13 native SQLite; unchanged tasks reused Gradle up-to-date results), and all 4 real database desktop workflows reran after the final visual changes. Core boundary verification passed. The unchanged 39 server integration cases were not rerun for this presentation-only increment; their prior successful baseline remains recorded above.
- Rebuilt ZIP/TAR distributions, confirmed workspace.css is packaged in the desktop jar, and verified final installed-launcher startup/shutdown. No JavaFX CSS parse errors were reported; the preexisting unnamed-module warning remains. Updated README with theme ownership and visual screenshot paths. No new TODO/FIXME, credentials, database schema changes, or unresolved blockers introduced. Local Windows verification only; cross-platform/remote CI and other previously listed open items remain.
- STOPPED — requested desktop visual refinement is complete.

### FAILED — Visual verification issues resolved in this session

- Initial MySQL/MariaDB desktop assertions expected the SQLite text field's immediate parent to be invisible. Repro: desktopTest with the new labeled VBox nested inside the hidden local HBox failed the old direct-parent assertion. The UI hid the file form correctly; corrected the test to check effective ancestor visibility. All four final workflows pass.
- The first resize-wait test failed compilation because DesktopWindow.root() returns Parent, which has no getWidth()/getHeight(). Repro: compileTestJava after adding the minimum-window wait. Corrected the check to use Stage.getScene() dimensions; final build and workflow suite pass. No broken state remains.


### 2026-10-04 — Next major feature selection prepared

- Maintainer requested the next big feature. Reviewed the complete project state, Milestone 1 requirements, existing foundation plan, and desktop scheduling boundaries; the four-database MVP and visual refinement remain complete with no broken state.
- Recommended multiple SQL tabs on the existing single connection: independent editors/results/settings, cancellation ownership, and unsaved-edit protection. Alternative increments are displayed-result CSV/JSON export and richer keys/indexes/relationship metadata. These are materially different UX/features, so asked the maintainer to select under AGENTS.md rather than silently implement a new direction.
- NEEDS DECISION — choose the next major feature from the presented options (or specify another). Existing secure credential storage, parser and packaging decisions remain deferred.
- Validation: read-only preparation and this log only; no executable source or dependency changes, so no tests rerun. The last verified build/four-workflow results remain the baseline. No implementation shortcuts or TODOs added.
- STOPPED — implementation awaits feature selection; existing application remains buildable.
