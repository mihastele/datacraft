# ADR 0002: Java 21, Gradle, JavaFX, and Apache-2.0

- Status: Accepted
- Date: 2026-10-04
- Decision source: Explicit maintainer selection

## Decision

Use Java 21 as the language and bytecode baseline, Gradle as the build tool,
JavaFX for the desktop client, and Apache-2.0 for this project.
The build starts with Gradle 9.3.1, matching the locally installed stable
patch release. Its wrapper is pinned and verifies the distribution SHA-256.
No preview Java features are enabled. Builds require an installed JDK 21.

Use build modules to establish boundaries; defer JPMS until a demonstrated
need. The first module is datacraft-core. Add clients and concrete adapters
when they contain working, tested workflows rather than empty scaffolds.
JavaFX is selected but no desktop or JavaFX dependency is introduced yet.

## Testing and dependencies

JUnit Jupiter 6.0.3 is the test framework, with the matching platform
launcher. JUnit is maintained (the 6.0.3 release is documented by the
project) and licensed under EPL-2.0. It is used only in the test classpath,
not redistributed in the core artifact; Apache-2.0 project sources remain
separate. Preserve its license if distributing test dependencies later.
JUnit is preferred over TestNG or a custom test runner because it is a
widely used Java test framework with direct Gradle integration.

- [JUnit release notes](https://docs.junit.org/6.0.3/release-notes.html)
- [JUnit license](https://github.com/junit-team/junit-framework/blob/r6.0.3/LICENSE.md)
- [Gradle release notes](https://docs.gradle.org/9.3.1/release-notes.html)

The core has no runtime third-party dependencies. Test dependency versions
are exact and resolved dependencies are recorded in Gradle lockfiles.
The boundary check inspects both the runtime classpath and compiled bytecode
with JDK jdeps. Only java.base is allowed at this stage; any expansion of
that allowance requires an explicit architectural review.

## Consequences

Developers do not need a global Gradle installation after wrapper setup.
Core verification is headless and requires no database or credentials.
The desktop client and production packaging remain future deliverables;
JavaFX version selection and license review occur before adding it.
