# Runtime dependency notices and sources

DataCraft's own source is Apache-2.0. Dependencies remain separate works
under their original licenses; their jars are not modified.

- pgJDBC 42.7.13: BSD-2-Clause. Notice retained as pgjdbc-LICENSE.
  [Source](https://github.com/pgjdbc/pgjdbc/tree/REL42.7.13).
- JNA 5.19.1: Apache-2.0 license option selected from its dual license.
  Retained jna-LICENSE, jna-AL2.0, and jna-libffi-LICENSE for the bundled
  native support. Only the core JNA jar is used; no jna-platform dependency.
  [Source and licensing](https://github.com/java-native-access/jna/tree/5.19.1).
- Xerial sqlite-jdbc 3.53.4.0: Apache-2.0, with its permissive Zentus notice
  retained as sqlite-jdbc-LICENSE and sqlite-jdbc-LICENSE.zentus.
  Its bundled SQLite engine is public domain.
  [Source](https://github.com/xerial/sqlite-jdbc/tree/3.53.4.0),
  [SQLite copyright](https://www.sqlite.org/copyright.html).
- MariaDB Connector/J 3.5.10: LGPL-2.1-or-later. Unmodified, replaceable jar;
  retained mariadb-connector-j-LICENSE and mariadb-connector-j-COPYRIGHT.
  It supplies connectivity for both MySQL and MariaDB.
  [Corresponding source](https://github.com/mariadb-corporation/mariadb-connector-j/tree/3.5.10),
  [source archive](https://github.com/mariadb-corporation/mariadb-connector-j/archive/refs/tags/3.5.10.zip).
  Users may replace the library jar in the distribution's lib directory and
  modify/debug the library under its license; DataCraft imposes no restriction
  on those library rights.
- checker-qual 3.55.1: MIT, as declared by the publisher POM and the retained
  checker-qual-LICENSE.txt notice extracted from the unmodified jar. This annotation
  jar is a pgJDBC runtime dependency.
  [Source](https://github.com/typetools/checker-framework/tree/checker-framework-3.55.1).
- OpenJFX 21.0.12 base, graphics, and controls: GPLv2 with Classpath Exception.
  LICENSE and ADDITIONAL_LICENSE_INFO are retained, together with graphics
  native-library notices. The Classpath Exception allows linking independent
  application modules under Apache-2.0.
  [Corresponding upstream source](https://github.com/openjdk/jfx21u/tree/21.0.12%2B3),
  [source archive](https://github.com/openjdk/jfx21u/archive/refs/tags/21.0.12%2B3.zip).

The application distribution copies this directory alongside the unmodified
runtime jars. JUnit and Testcontainers are test-only and are not included
in that distribution. Their licensing rationale is documented in ADRs
0002 and 0003. A public release must preserve dependency notices and provide
corresponding source in accordance with upstream redistribution terms.
