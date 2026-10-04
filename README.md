# DataCraft

DataCraft is an open-source database IDE with PostgreSQL, SQLite, MySQL, and MariaDB support.
Its primary implementation language is **Java**. It is a modular Java
platform whose desktop application is one client of a UI-independent core.

The platform owns database metadata, SQL intelligence, query execution,
workspace services, and safety policies. Database adapters implement
capability-based integration contracts. Desktop components consume
application services rather than implementing database business logic.

Read [MISSION.md](MISSION.md) for the product brief and roadmap,
[the Java architecture decision](docs/adr/0001-modular-java-platform.md)
for the accepted architectural direction, and
[the project state](.agentic/PROJECT-STATE.md) for progress and open decisions.

## Build and verify

Install a JDK 21 and make it available through `JAVA_HOME` or `PATH`.
The wrapper downloads the pinned Gradle 9.3.1 distribution on its first run.
Dependency downloads require network access on the first build.

Windows PowerShell:

```powershell
.\gradlew.bat --no-daemon clean build
```

Linux/macOS:

```sh
bash ./gradlew --no-daemon clean build
```

The build runs JUnit tests, including real native SQLite tests on temporary
files and, on Windows, native Credential Manager round trips with disposable
random-ID entries. The two Windows-only credential tests are explicitly skipped
on other platforms. It checks that compiled core code depends only
on `java.base` and has no runtime library dependencies. Test reports are in
`datacraft-core/build/reports/tests/test/index.html` and the core JAR is in
`datacraft-core/build/libs/`. CI runs the same build on Windows and Linux,
plus PostgreSQL/MySQL/MariaDB integration suites and all desktop workflows on Linux.

To verify the server adapters, start a Docker engine with Linux-container
support and run:

```powershell
.\gradlew.bat --no-daemon clean build integrationTest
```

On Linux/macOS use `bash ./gradlew --no-daemon clean build integrationTest`.
Testcontainers provisions and removes dedicated PostgreSQL 17, MySQL 8.4,
and MariaDB 11.4 containers. Images are pinned by digest and credentials are
generated at runtime. Test fixtures initialize only these newly created servers.
The first run may download database and helper images. No existing database
or connection configuration is used. Missing Docker fails the integration
task. `build` intentionally runs only the Docker-free tests; integration
tests must also pass when changing the adapter. Integration reports are in
`datacraft-postgresql/build/reports/tests/integrationTest/index.html` and
`datacraft-mysql/build/reports/tests/integrationTest/index.html`.

When intentionally updating dependency versions, regenerate and review
the module lockfiles:

```sh
bash ./gradlew :datacraft-core:dependencies :datacraft-postgresql:dependencies :datacraft-sqlite:dependencies :datacraft-mysql:dependencies :datacraft-platform:dependencies :datacraft-sql:dependencies :datacraft-desktop:dependencies --write-locks
```

Use `gradlew.bat` on Windows. Run the build again after reviewing the changes.

JavaFX native classifiers follow the build
machine's platform; distribution archives must be built for their target OS.

## SQL intelligence

Each query tab analyzes SQL locally in the background using JSQLParser 5.4.
The diagnostics below the editor report incomplete or unsupported syntax.
Open **SQL structure** to inspect the parsed AST; selecting a node selects its
source text in the editor. Analysis is advisory: the database and existing
read-only execution policies remain authoritative.

Press **Ctrl+Space** (or Command+Space where the OS allows it) for completion.
Use Up/Down to choose, Enter/Tab to insert, or Escape to dismiss. Suggestions
include keywords, namespaces, discovered tables/views, and columns resolved
through table aliases. For example, `SELECT u.email FROM public.users u WHERE u.`
suggests the cached columns of `users`.

Metadata remains lazy: expand a schema/database in the explorer to load its
relations, and select a relation to load its columns. Completion uses that
connection's cached metadata and does not execute user SQL or fetch row data.
Refresh clears the cache; disconnect clears it, and stale suggestions are
rejected after edits or metadata changes. No external service receives SQL.

This first slice supports direct table aliases and joins within the current
SELECT scope. It does not resolve CTE/derived-table output columns, correlated
outer aliases, or ambiguous unqualified tables across schemas. Unsupported
vendor syntax can still be submitted to the existing execution pipeline.
There is no claim of server-equivalent validation, formatting, refactoring,
semantic error checking, or automatic database-wide introspection.
See [ADR 0008](docs/adr/0008-sql-intelligence.md) for boundaries and limits.

## Run the MVP

Windows:

```powershell
.\gradlew.bat :datacraft-desktop:run
```

Linux/macOS: `bash ./gradlew :datacraft-desktop:run`.

Choose **PostgreSQL**, **SQLite**, **MySQL**, or **MariaDB** in Database type. For SQLite, browse to an
existing database file (or enter its path); the adapter never creates a file
and opens it read-only. Server credentials and TLS fields are hidden. The
explorer shows tables/views under `main`. To switch database types, disconnect
first; this increment keeps one active connection.

For PostgreSQL, enter host, port, database, username, and password. TLS defaults to VERIFY_FULL;
DISABLED is an explicit choice for a controlled local/test server. The driver
uses its default certificate configuration for verified TLS. Select the
environment so it stays visible in the workspace. Use a minimally privileged
database role: this client is not a sandbox for privileged database functions.

For MySQL/MariaDB, enter server fields and your database name (default port
3306). Accessible databases appear in the explorer. Both use the same MariaDB
Connector/J driver and verified TLS by default. MySQL/private certificate
chains must be trusted by the JDK truststore. MariaDB 11.4+ can authenticate
its generated TLS certificate through the driver's fingerprint/password
verification. Automatic RSA key retrieval is disabled; modern MySQL password
authentication should use verified TLS. Plaintext is an explicit local/test
choice and may not support your server's authentication plugin. DataCraft
never changes server users or authentication configuration.

The MySQL/MariaDB console pins `NO_BACKSLASH_ESCAPES`; use doubled quotes in
strings. Executable version comments, optimizer hints, user variables,
SELECT INTO, scripts, and unbound parameters are rejected. Query writes are
blocked by server-enforced read-only transactions. See
[ADR 0006](docs/adr/0006-mysql-mariadb.md) for dialect, TLS, authentication,
and tested-version details.

Give a connection a name and click **Save**, or **Connect** to save it and open
the workspace. Leave the name blank for a temporary connection. **Saved connection**
reloads its details; **New** starts another profile, and **Delete** removes the
selected profile and its stored password after confirmation. Disconnect before
editing or switching profiles.

**Password storage** defaults to **Do not store**. On Windows, explicitly select
**Store securely** to save the password in Windows Credential Manager. Passwords
never go into the profile file. Leave the password field blank to use an existing
stored password, or enter a replacement. Changing connection settings with a
stored-password preference requires entering the password again. Selecting
**Do not store** and saving/connecting removes the old stored password. macOS and
Linux save basic profiles and require entering a password for each connection;
their secure-store providers are deferred. Existing Windows storage preferences
are retained when loading a profile elsewhere; removing those credentials must
be done on Windows.

Basic details are stored in `connections.properties` (version 1) under
`%LOCALAPPDATA%\DataCraft` on Windows, `~/Library/Application Support/DataCraft`
on macOS, and `$XDG_CONFIG_HOME/datacraft` or `~/.config/datacraft` on Linux.
The file includes names, host/port/database/username or SQLite paths, environment,
TLS, timeouts, and password-storage preferences with opaque UUID references.
It contains no passwords or query text. Unsupported/corrupt files are preserved
and reported; there is no automatic reset. Writes use a lock and atomic replacement.

Expand a connection's schema/database to load its tables/views, select a relation to inspect columns,
and use the SQL console to run one SELECT or WITH … SELECT. Ctrl+Enter executes
selected text when present, otherwise the whole editor. Run and Cancel are
available in each tab's toolbar. **+ Query** adds an independent editor/results
tab, up to 20 per window. Each retains its own row limit and timeout. One query
runs at a time on the active connection; switching tabs does not change the owner
of its results or Cancel button. Edited tabs show a dot and require discard
confirmation on closure. Query text remains in memory until the window closes;
disconnect clears every result grid and preserves the editors. Queries use read-only transactions; write commands,
scripts, and transaction controls are blocked. Results display at most 1000
rows (default 500), 128 columns, 4096 characters per cell, and 2000000 retained
value characters. NULL is distinct from empty text; clipped results are marked.
All values are displayed as text. Query execution and cancellation have finite
timeouts; connection cleanup happens on disconnect and window close. SQLite
lock waits are capped at one second; expressions may have dynamic/unknown
types. Parameter binding, attached databases, encrypted files, and write mode
are outside this increment. See [ADR 0005](docs/adr/0005-sqlite-and-adapter-selection.md)
for adapter selection, dialect handling, and file/query limitations.

To create launcher scripts with the runtime libraries:

```powershell
.\gradlew.bat :datacraft-desktop:installDist
.\datacraft-desktop\build\install\datacraft\bin\datacraft.bat
```

On Linux/macOS the installed launcher is `datacraft-desktop/build/install/datacraft/bin/datacraft`.
JDK 21 is required; there is no bundled JDK or native installer yet. ZIP/TAR
archives are generated under `datacraft-desktop/build/distributions/` by `build`.
The non-modular launcher can emit JavaFX's unnamed-module warning; JPMS and
native packaging are deferred decisions.

## Verify the desktop workflow

With Docker running and a desktop display available:

```powershell
.\gradlew.bat --no-daemon clean build integrationTest :datacraft-desktop:desktopTest
```

On headless Linux, run the desktop task under `xvfb-run -a`.
The tests drive the real JavaFX controls against a newly provisioned PostgreSQL
database, a temporary SQLite file, and separate MySQL/MariaDB servers,
check cancellation/reuse and disconnect cleanup, and save screenshots in
`datacraft-desktop/build/desktop-screenshots/workspace.png` and
`datacraft-desktop/build/desktop-screenshots/sqlite-workspace.png`, with
`mysql-workspace.png` and `mariadb-workspace.png` alongside them.
The same directory includes `disconnected-workspace.png` and
`minimum-workspace.png` for empty-state and minimum-window visual checks.
`multi-tab-workspace.png` captures the saved-profile/multiple-tab workflow.
Reports are under `datacraft-desktop/build/reports/tests/desktopTest/`. The task fails when Docker
or a display is unavailable; it never silently skips verification. CI is
configured to run it under Xvfb on Linux. The installed launcher accepts
`--verify-launch` to check startup/shutdown without accessing a database.

If Corretto on Windows reports `Unable to establish loopback connection`
with `Invalid argument: connect`, use an existing directory with a full
path for the JDK's temporary Unix socket files:

```powershell
New-Item -ItemType Directory -Path '.gradle/socket-tmp' -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=' + (Resolve-Path '.gradle/socket-tmp').Path
.\gradlew.bat --no-daemon clean build
Remove-Item Env:JAVA_TOOL_OPTIONS
```

This workaround is local to the current shell. Preserve any preexisting
`JAVA_TOOL_OPTIONS` value when using it in an already configured environment.

## Status

Read-only PostgreSQL, SQLite, MySQL, and MariaDB desktop workflows implemented
on the tested core, with one active connection.
`datacraft-core` contains capabilities, non-secret connection settings,
environment classifications, metadata models, and driver-independent sessions.
`datacraft-postgresql`, `datacraft-sqlite`, and `datacraft-mysql` connect,
introspect, and execute
bounded read queries using separate native dialects. An immutable adapter
registry selects the strategy from typed server/file connection profiles;
shared result accumulation enforces consistent display limits.
`datacraft-desktop` is the JavaFX connection/explorer/editor/results client.
Its light workspace uses a navy header, teal actions, labeled connection fields,
and distinct query/result sections. The theme is centralized in
`datacraft-desktop/src/main/resources/io/datacraft/desktop/workspace.css`;
database services remain independent of presentation styles.
Application services own sessions and consume transient password arrays.
Calls run off the UI thread, with a separate cancellation worker.
See [ADR 0004](docs/adr/0004-readonly-desktop-mvp.md) for query safety, limits,
and credential lifetime details, and [ADR 0007](docs/adr/0007-profiles-and-query-tabs.md)
for saved profiles, optional Windows credentials, and multiple query tabs.
`datacraft-platform` implements file persistence and native credentials behind
core interfaces. Write controls, persisted query history, pagination/export,
and macOS/Linux credential providers are not implemented.
`datacraft-sql` supplies local AST analysis and cached schema/alias-aware
completion through editor-independent immutable contracts; its runtime
dependency boundary is checked by the build. See
[ADR 0008](docs/adr/0008-sql-intelligence.md) for the initial SQL intelligence slice.
PostgreSQL version-matrix and positive TLS tests remain open.
See [the foundation plan](docs/foundation-plan.md) for the next deliverables
and [ADR 0002](docs/adr/0002-foundation-toolchain.md) for technology choices.

## License

DataCraft is licensed under [Apache-2.0](LICENSE).
Dependencies retain their own licenses, including OpenJFX's GPLv2 with
Classpath Exception, pgJDBC's BSD-2-Clause, and MariaDB Connector/J's LGPL-2.1-or-later.
[Notices and source links](docs/licenses/README.md) accompany the distribution.
