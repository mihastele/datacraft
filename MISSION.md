# DataCraft

## Genesis

DataCraft is an open-source, enterprise-grade database IDE.

The long-term objective is straightforward but deliberately ambitious:

> **Build an open-source database IDE powerful and polished enough that professional developers, DBAs, data engineers, and engineering teams can use it as their primary database environment.**

DataCraft is not intended to be another lightweight SQL client.

It is not a weekend CRUD application.

It is not an AI chat interface wrapped around a database connection.

It is not a clone built by reproducing another application's UI.

DataCraft should become a serious engineering tool for exploring databases, writing and understanding SQL, manipulating data, analyzing schemas, inspecting query performance, managing database objects, and safely working with production systems.

The benchmark is simple:

> A developer who currently depends on a commercial database IDE should eventually be able to uninstall it and use DataCraft instead.

That will take time.

We accept that.

Correct architecture, reliability, extensibility and maintainability are more important than rapidly accumulating features.

---

# 1. Product Philosophy

DataCraft should follow seven principles.

### 1. Database IDE, not database viewer

Displaying tables and executing SQL is the baseline.

The product becomes interesting when it understands:

- schemas
- SQL semantics
- relationships
- database objects
- dependencies
- query plans
- dialects
- transactions
- migrations
- permissions
- execution context

DataCraft should help developers **reason about databases**.

### 2. PostgreSQL first, architecture for many databases

The first serious implementation targets PostgreSQL.

Do not prematurely implement ten mediocre database drivers.

Instead:

> Make PostgreSQL excellent while ensuring PostgreSQL-specific behavior does not leak throughout the core architecture.

Once the abstraction proves itself, expand toward:

- SQLite
- MySQL
- MariaDB
- SQL Server
- Oracle
- CockroachDB
- ClickHouse
- DuckDB

Non-relational systems may eventually be supported through separate capability models.

### 3. Safety is a product feature

Database IDEs frequently connect to systems containing valuable production data.

DataCraft must therefore treat destructive operations as security-sensitive actions.

Connections should support environment classifications:

- LOCAL
- DEVELOPMENT
- TEST
- STAGING
- PRODUCTION

Production environments may enable stricter policies.

Examples:

- read-only mode
- destructive-query warnings
- transaction requirements
- confirmation before DDL
- confirmation before unrestricted UPDATE/DELETE
- protection against accidental TRUNCATE/DROP
- query timeout policies

The UI should make the current environment unmistakable.

### 4. Local-first

Normal database development should not require a DataCraft cloud account.

Connection definitions, workspace configuration and query history should work locally.

Credentials must never be stored as plaintext configuration.

Use operating-system credential facilities where possible.

### 5. Extensible

Database implementations must live behind well-defined interfaces.

Long term, third parties should be capable of implementing database adapters without modifying DataCraft core.

### 6. AI is optional

DataCraft must remain excellent without AI.

AI functionality should consume stable DataCraft APIs rather than become intertwined with core database logic.

Users should eventually be able to choose:

- no AI
- local models
- hosted providers
- external agents

### 7. Correctness beats velocity

Database software can destroy data.

Prefer boring, testable engineering over clever shortcuts.

---

# 2. Product Architecture

Conceptually:

```text
┌──────────────────────────────────────────────┐
│                 DataCraft UI                 │
│                                              │
│ Explorer | Editor | Results | Plans | Tools │
└──────────────────────┬───────────────────────┘
                       │
                Application Services
                       │
     ┌─────────────────┼──────────────────┐
     │                 │                  │
Workspace          SQL Engine        Query Engine
     │                 │                  │
Projects           Parsing           Execution
History            AST               Cancellation
Settings           Analysis          Transactions
Secrets            Completion        Streaming
                   Resolution        History
                       │
              Schema / Metadata Model
                       │
              Database Adapter API
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   PostgreSQL        SQLite         MySQL
    Adapter          Adapter        Adapter
```

UI components must not contain database-specific business logic.

---

# 3. Core Domain Model

Create explicit internal representations for database metadata.

Conceptually:

```text
DatabaseConnection
DatabaseServer
Database
Schema
Table
Column
PrimaryKey
ForeignKey
UniqueConstraint
Index
View
MaterializedView
Sequence
Routine
Trigger
DataType
Role
Permission
```

These representations should be independent of any particular driver whenever practical.

Vendor-specific metadata can be represented through extension mechanisms.

---

# 4. Database Adapter API

Database integrations expose capabilities through an adapter.

Conceptually:

```text
DatabaseAdapter
 ├── connect()
 ├── disconnect()
 ├── ping()
 │
 ├── introspect()
 ├── refreshObject()
 ├── listSchemas()
 ├── listTables()
 ├── describeObject()
 │
 ├── execute()
 ├── cancel()
 ├── beginTransaction()
 ├── commit()
 ├── rollback()
 │
 ├── explain()
 ├── generateDDL()
 │
 └── capabilities()
```

Do not assume every database supports every feature.

Adapters advertise capabilities.

Example:

```text
Capabilities
  transactions
  savepoints
  schemas
  materializedViews
  storedProcedures
  explainPlans
  editableResults
  returningClause
  multipleDatabases
```

Application code should generally ask:

```text
adapter.capabilities().supports(X)
```

rather than:

```text
if database == POSTGRESQL
```

---

# 5. Query Execution Engine

Query execution is infrastructure, not a UI callback.

The engine must eventually support:

- asynchronous execution
- cancellation
- timeouts
- multiple result sets
- transaction state
- streaming results
- pagination
- parameterized queries
- execution statistics
- warnings/notices
- connection recovery
- query history

Never assume result sets fit into memory.

Large result sets must be processed incrementally.

The UI must remain responsive while queries execute.

---

# 6. SQL Intelligence Engine

This is one of DataCraft's most important long-term differentiators.

The editor should evolve from:

```text
text editor
```

into:

```text
SQL IDE
```

The SQL subsystem should eventually provide:

- tokenization
- parsing
- AST generation
- dialect awareness
- semantic analysis
- symbol resolution
- schema-aware completion
- diagnostics
- formatting
- navigation
- find usages
- safe rename
- refactoring
- documentation
- query analysis

Example:

```sql
SELECT u.email
FROM users u
WHERE u.
```

Completion should understand that `u` resolves to `users` and suggest columns from that table.

If:

```sql
SELECT customer.naem
```

references a nonexistent column, DataCraft should eventually identify the problem before execution.

The SQL engine must not depend on the visual editor implementation.

---

# 7. Schema Introspection

Metadata discovery should not mean repeatedly reloading the entire database.

Design for:

- initial introspection
- lazy introspection
- cached metadata
- incremental refresh
- targeted object refresh
- invalidation after DDL

If the user executes:

```sql
ALTER TABLE users ADD COLUMN nickname TEXT;
```

DataCraft should eventually understand that metadata associated with `users` may now be stale.

---

# 8. Desktop UX

Initial workspace concept:

```text
┌──────────────┬───────────────────────────────────┐
│ DATABASES    │ query.sql                         │
│              │                                   │
│ production   │ SELECT *                          │
│ └─ app       │ FROM users                        │
│    └─ public │ WHERE active = true;              │
│       ├ users│                                   │
│       └ posts├───────────────────────────────────┤
│              │ RESULTS                           │
│              │ id │ email │ active               │
│              │────┼───────┼────────              │
│              │ 1  │ ...   │ true                 │
└──────────────┴───────────────────────────────────┘
```

Primary surfaces:

- connection manager
- database explorer
- SQL editor
- result grid
- object inspector
- query history
- execution plan viewer
- schema editor
- import/export tools
- settings

Keyboard-driven workflows are first-class.

---

# 9. Result Grid

The result grid deserves its own subsystem.

Eventually support:

- virtualization
- sorting
- filtering
- copying
- NULL visualization
- binary data handling
- JSON inspection
- column resizing
- column pinning
- row editing
- insertion
- deletion
- bulk editing
- generated SQL preview
- CSV export
- JSON export
- clipboard formats

Edits must never silently mutate production data.

Pending changes should be inspectable before execution where practical.

---

# 10. Production Safety Engine

Create a centralized safety subsystem.

Do not scatter confirmation dialogs throughout UI components.

Conceptually:

```text
Query
  ↓
SQL Analysis
  ↓
Safety Policy
  ↓
ALLOW
WARN
CONFIRM
BLOCK
```

Policies may consider:

- connection environment
- read-only status
- SQL operation
- transaction state
- affected object
- presence of WHERE
- estimated impact

Examples:

```sql
DELETE FROM customers;
```

on production should receive substantially more scrutiny than:

```sql
SELECT *
FROM customers
LIMIT 20;
```

Safety controls must remain configurable because experienced database administrators may have different workflows.

---

# 11. Security

Security requirements are non-negotiable.

Never:

- log passwords
- commit credentials
- expose secrets in crash reports
- place passwords in ordinary workspace files
- send schema/data to external AI services without explicit authorization

Support secure credential storage.

Eventually support:

- SSL/TLS
- SSH tunneling
- proxies
- client certificates
- cloud authentication
- short-lived credentials

Logs must redact sensitive values.

---

# 12. Agent Interface

DataCraft should eventually expose a controlled automation interface.

Potential operations:

```text
list_connections()
inspect_schema()
describe_object()
search_objects()
read_ddl()
execute_readonly_query()
explain_query()
compare_schemas()
generate_migration()
```

Mutating operations must require stronger authorization.

An AI agent should not receive unrestricted arbitrary production database access merely because the user opened a chat window.

The agent API should distinguish between:

```text
READ_METADATA
READ_DATA
EXECUTE_QUERY
MODIFY_DATA
MODIFY_SCHEMA
ADMINISTRATE
```

This makes DataCraft potentially useful as both a human database IDE and an infrastructure layer for database-aware agents.

---

# 13. Testing Strategy

Testing is part of the architecture.

Maintain:

### Unit tests

For:

- parsers
- analyzers
- safety policies
- metadata transformations
- capability handling

### Integration tests

Run real database instances through containers.

For example:

```text
PostgreSQL 14
PostgreSQL 15
PostgreSQL 16
PostgreSQL 17
```

Test:

- connection
- introspection
- queries
- transactions
- cancellation
- DDL
- unusual data types
- failures

### End-to-end tests

Test critical user workflows.

Example:

```text
Create connection
→ connect
→ open console
→ execute query
→ inspect results
→ modify query
→ disconnect
```

Do not over-mock database behavior.

---

# 14. Milestone 0 — Foundation

Before chasing features:

1. establish repository architecture
2. choose desktop/runtime technologies
3. define module boundaries
4. define adapter API
5. define internal metadata model
6. implement configuration
7. implement logging
8. implement secure secret abstraction
9. establish testing infrastructure
10. establish CI
11. document architectural decisions

Deliverable:

> A boring but excellent foundation.

---

# 15. Milestone 1 — PostgreSQL Developer Preview

Goal:

> DataCraft becomes genuinely useful for everyday PostgreSQL querying.

Required:

- create/edit/delete connections
- secure credentials
- PostgreSQL connectivity
- SSL
- database explorer
- schema explorer
- table/view discovery
- column metadata
- SQL editor
- multiple query tabs
- execute selection
- execute statement
- cancellation
- result grid
- NULL rendering
- transaction controls
- query history
- CSV export
- JSON export
- reconnect handling
- environment classification

No AI required.

No MySQL required.

No ER diagram required.

---

# 16. Milestone 2 — Database IDE

Goal:

> DataCraft begins competing on developer intelligence.

Implement:

- SQL AST
- PostgreSQL dialect support
- schema-aware completion
- diagnostics
- symbol resolution
- object navigation
- formatting
- quick documentation
- object search
- improved introspection
- explain plans

At this point DataCraft should feel substantially different from a generic SQL client.

---

# 17. Milestone 3 — Database Engineering

Implement:

- table editor
- index management
- constraint management
- foreign-key tooling
- generated DDL
- schema diff
- migrations
- import/export
- ER visualization
- dependency navigation
- editable result sets

---

# 18. Milestone 4 — Multi-Database

Prove the adapter architecture.

Recommended order:

```text
PostgreSQL
    ↓
SQLite
    ↓
MySQL / MariaDB
    ↓
SQL Server
```

Do not compromise architecture merely to support another database quickly.

---

# 19. Milestone 5 — Enterprise & Automation

Investigate:

- plugin SDK
- organization policies
- shared connection templates
- secret-provider integrations
- cloud database authentication
- audit capabilities
- advanced production safeguards
- database monitoring
- agent API
- MCP/API integration
- local AI
- hosted AI providers

---

# 20. Rules for Agentic Development

The coding agent working on DataCraft must follow these rules.

### Rule 1 — Inspect before changing

Before implementing a substantial feature:

1. inspect relevant modules
2. understand existing abstractions
3. identify tests
4. explain the intended change
5. then modify code

Do not blindly append functionality.

### Rule 2 — Never fake functionality

No buttons that pretend to work.

No hardcoded demo database objects presented as real functionality.

No placeholder implementation silently returning success.

Incomplete functionality must be explicitly marked.

### Rule 3 — Preserve architectural boundaries

UI code must not become the database abstraction layer.

Database drivers must not become UI dependencies.

SQL analysis must not depend on a specific editor widget.

### Rule 4 — Prefer vertical slices

Instead of implementing twenty disconnected skeletons, implement one complete workflow.

Prefer:

```text
PostgreSQL connection
→ introspection
→ explorer
→ query
→ results
```

over:

```text
10 database logos
+ 30 empty menus
+ AI sidebar
```

### Rule 5 — Tests accompany infrastructure

Critical infrastructure changes require tests.

Especially:

- query execution
- transactions
- SQL analysis
- metadata
- destructive-query detection
- credential handling

### Rule 6 — Explain important architectural decisions

Record significant decisions as ADRs:

```text
docs/adr/
```

Example:

```text
0001-desktop-runtime.md
0002-database-adapter-boundary.md
0003-sql-parser-strategy.md
0004-secret-storage.md
```

### Rule 7 — Avoid premature abstraction

We are designing for multiple databases.

We are initially implementing one.

Do not create enormous generic frameworks based entirely on hypothetical future requirements.

Allow PostgreSQL to teach us what the abstraction needs.

### Rule 8 — No autonomous destructive operations

The development agent must never execute destructive operations against databases that have not explicitly been provisioned as development/test resources.

---

# 21. Definition of Success

The project has succeeded at its first major objective when its maintainers naturally choose DataCraft for their own normal PostgreSQL work.

The ultimate test is not:

> “Does DataCraft have a SQL editor?”

It is:

> “Why would I open another database IDE instead?”

Every major feature should move us toward having fewer answers to that question.

---

# Final Directive

Build DataCraft patiently.

Do not optimize for screenshots.

Do not optimize for GitHub feature-count vanity.

Do not optimize for AI hype.

Optimize for the moment when a professional developer connects DataCraft to an important database, works for several hours, and forgets that the tool itself is there.

That is the standard.

**DataCraft should make databases feel understandable, navigable and safe.**

Now build the foundation that can eventually earn that reputation.
