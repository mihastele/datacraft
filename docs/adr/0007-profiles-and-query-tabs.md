# ADR 0007: Saved connections, optional Windows credentials, and query tabs

Date: 2026-10-04

Status: Accepted. The maintainer requested named connection definitions,
password storage only after a manual dropdown choice, a workspace that opens
after connecting, multiple query tabs, and the database hierarchy. They explicitly
selected Windows Credential Manager via JNA first; other platforms prompt for
session passwords. This extends ADR 0004's transient-connection MVP scope.

## User workflow

Enter connection details and a name. Save creates/updates a profile; Connect
saves a named profile before connecting. An unnamed connection stays temporary.
Choose a saved profile to reload its settings. Disconnect before changing the
active profile. New starts a fresh definition; Delete confirms removal of both
the definition and any stored credential.

Password storage defaults to Do not store. The user must choose Store securely
to enable storage. A loaded profile preserves the preference already chosen by
the user. SQLite has no password preference. Saving or connecting with Do not
store removes a previously retained credential. Empty password input can reuse
an existing credential only when the profile settings are unchanged; saving
changed settings with secure storage requires entering the password again.

A successful connection opens the query workspace and connection/schema/table
hierarchy. Each query tab owns its SQL, results, row limit and timeout. The
connection remains single-owner: one query runs at a time, with cancellation
bound to the initiating tab. Other tabs can be edited during execution. Up to
20 documents retain bounded result grids. Dirty markers mean edited in-memory
query text, not saved SQL files. Closing an edited tab or the application asks
for discard confirmation; a running tab cannot close. Disconnect clears all
result grids and metadata while keeping editors. SQL/history persistence and
simultaneous active connections remain separate future features.

## Boundaries

The java.base-only core defines SavedConnection, ConnectionRepository and
CredentialStore contracts, with ConnectionProfiles owning consent, credential
lifetime, and profile operations. DesktopController schedules these services
off the JavaFX thread. ConnectionPane maps user choices; QueryPane owns one
document's presentation. DesktopWindow coordinates active connection and query
ownership. It captures the originating query pane before asynchronous execution.

The new datacraft-platform module owns filesystem/OS implementations. The
composition root injects stores; JavaFX and native types never enter the core.
Tests inject temporary repositories rather than accessing the user's profiles.

## Non-secret profile format

Version 1 is UTF-8 Java properties with a version, profile count, and numbered
profile records. It stores UUID, name, kind, host/port/database/username or SQLite
path, environment, TLS, timeout, and a boolean password-storage preference. The
UUID is the opaque credential reference. Passwords and SQL/results are absent.
Java's standard properties format avoids another serialization dependency.

Paths are the dedicated DataCraft directory in LOCALAPPDATA on Windows,
Library/Application Support on macOS, and XDG_CONFIG_HOME or .config on Linux.
POSIX directories/files use owner-only permissions; Windows uses the current
user's application-data directory and inherited ACLs. Files are bounded to
1 MiB and 200 profiles. Unknown versions/fields, invalid values, duplicate IDs,
empty files and malformed UTF-8 fail without replacing the file. Future format
changes require an explicit migration/version decision and fixture coverage.
DataCraft owns no application database schema; these are file records, not
user-content SQL tables or changes to a connected database.

Persistent repositories serialize profile/credential operations with a file
lock. Snapshots also detect stale writes. File writes are forced to a temporary
file and atomically replace the prior snapshot; unsupported atomic replacement
fails instead of partially overwriting it. Failed credential replacement restores
the prior secret; failed new-profile writes remove their new secret. Revocation
and deletion remove the secret first: if the subsequent file write fails, the
old profile can remain but its credential is absent, requiring re-entry/retry.
Credential removal is never rolled back against the user's consent. Unexpected
cleanup failures are surfaced; no cross-resource crash-proof transaction is
claimed between the filesystem and Windows Credential Manager.

## Credential provider and dependency choice

Windows uses native CredWriteW/CredReadW/CredDeleteW/CredFree generic credentials
with a DataCraft/connection/UUID target. Local-machine persistence retains them
for the current Windows user on this computer across logon sessions; it does
not make them available to every user. No encrypted file, master key, plaintext
fallback, command-line secret or shell credential helper is introduced.
Processes running as the same Windows user can access the user's credentials;
this follows the OS credential model, not a separate application master password.

JNA 5.19.1 is pinned and configuration locks are committed. Official Maven
metadata identifies the June 2026 release, and upstream activity in August 2026
confirms maintenance. Its Apache-2.0 option is selected from the dual license;
upstream and bundled libffi notices accompany the unmodified jar. Chosen over
custom JNI or a PowerShell subprocess for standard Java/native bridging without
passing credentials in command lines or writing helper files. Only the core
JNA jar is needed; this API is not in jna-platform, so the narrow Windows
structure/function binding lives in datacraft-platform.

Passwords use caller-owned char arrays and a bounded UTF-16 native blob.
Temporary arrays/native memory are wiped; read buffers are cleared before
CredFree. Windows' 2560-byte generic credential limit is enforced. JavaFX's
PasswordField and JDBC can still create transient immutable String copies,
as noted in ADR 0004. No raw driver/credential error or profile contents are
written to application logs. Missing native storage never falls back to files.
macOS/Linux can persist basic details but require session password input;
editing/deleting a Windows-stored credential preference requires Windows.

## Verification

Core tests cover default no-storage behavior, explicit consent/revocation,
deletion, wiping, corrupt reads, unavailable stores, rollback/orphan cleanup,
changed-endpoint checks and profile validation. Platform tests round-trip all
four database kinds, reject bad/oversized/newer files, check stale snapshots and
whole-operation locks, and exercise real Windows native credentials using
random-ID entries removed in finally blocks. The two Windows-only native tests
are explicitly conditional on that OS; other coverage runs on every platform.

Actual JavaFX workflows exercise all four database choices and a saved-profile
restart/reload workflow. On Windows the latter manually selects secure storage,
connects without refilling the password field, then revokes storage. It verifies
independent tab settings/results, switching during execution, cancellation
ownership, close protection, disconnect cleanup and the real tab close control.
Screenshots include connected, disconnected, minimum-window and multiple-tab
states. Cross-platform/native-store expansion and remote CI remain open.

Sources: [JNA 5.19.1](https://github.com/java-native-access/jna/tree/5.19.1),
[JNA changes](https://github.com/java-native-access/jna/blob/5.19.1/CHANGES.md),
[Windows credential write API](https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew),
[credential structure](https://learn.microsoft.com/en-us/windows/win32/api/wincred/ns-wincred-credentialw).
