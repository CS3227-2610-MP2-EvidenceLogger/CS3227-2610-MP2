# EvidenceLogger minimal architecture

## 1. Goals and constraints

### Confirmed constraints

- The application is an offline, single-workstation Java SE 25 and JavaFX 25 desktop application with distinct Evidence Custodian and Investigator views.
- The approved persistence stack is one local SQLite database accessed through Xerial SQLite JDBC and plain JDBC repositories. There is no server, web framework, ORM, or cloud service.
- Role and current case-assignment restrictions must be enforced in services, not only by hiding UI controls.
- Every successful custody-changing operation and its audit event or events commit atomically. Audit history is append-only.
- Gradle 9.1 or newer, through the wrapper, builds and tests the application. JUnit Jupiter is used for service and database tests.
- Java's standard logging API provides local diagnostic logs. Passwords must never appear in a diagnostic log.
- Java 25 is an installation prerequisite. The release must explicitly package the required JavaFX libraries and be smoke-tested on Windows, macOS, and Linux.
- Attachments and all backup and restore behavior are outside the approved MVP.

### Architectural priorities

The architecture favors a small number of explicit layers, constructor wiring, immutable data passed between layers, and real SQLite integration tests. It applies SRP by giving UI, workflow coordination, domain rules, persistence, and infrastructure separate responsibilities. It applies DRY by centralizing authorization checks, transaction handling, password hashing, ID/time generation, database mapping, and error translation. It does not introduce abstractions for deferred features.

## 2. System shape and dependency direction

```text
JavaFX views/controllers
          |
          v
application services + authorization
       |              |
       v              v
pure domain       repository interfaces
                         |
                         v
                 JDBC repositories
                         |
                         v
                       SQLite
```

Dependencies point inward. UI and JDBC code may depend on the service/domain API; domain code does not depend on JavaFX, JDBC, SQLite, or the filesystem. Repository implementations do not call UI code. Controllers never open database connections or construct SQL.

`EvidenceLoggerLauncher` is a small public launcher that does not extend `javafx.application.Application`; it invokes the actual `EvidenceLoggerApplication`. This follows the launcher arrangement needed for a JavaFX runnable fat JAR. `EvidenceLoggerApplication` constructs the object graph explicitly and owns startup and shutdown.

The JavaFX application should have one in-process background executor for database calls so the JavaFX Application Thread remains responsive. A controller disables the initiating control while a command is running, then renders either the service result or a user-readable error back on the JavaFX thread. This executor is infrastructure, not a source of business concurrency rules.

## 3. Proposed packages and responsibilities

Use a single application module and a root package such as `evidencelogger`. The final organization-qualified prefix may be chosen when the Gradle project is created.

| Package | Responsibility | Must not do |
| --- | --- | --- |
| `evidencelogger.app` | Launcher, JavaFX lifecycle, manual dependency wiring, application-data path selection, startup migration, graceful shutdown. | Contain workflow rules or SQL. |
| `evidencelogger.app.session` | Observe authentication state to drive navigation on sign-in/sign-out. | Own authorization state, decide case access, or trust a UI-supplied actor ID. |
| `evidencelogger.ui.common` | Shared code-built controls, navigation shell, validation display, async task helper, and error presentation. | Authorize actions or mutate domain state. |
| `evidencelogger.ui.login` | Login form and controller. | Query user tables directly. |
| `evidencelogger.ui.custodian` | Minimal Custodian screens/controllers for cases, assignments, locations, registration, request decisions, handoffs, returns, and history. | Reimplement transition or authorization rules. |
| `evidencelogger.ui.investigator` | Minimal Investigator screens/controllers for assigned-case search, requests, acknowledgment, notes, returns, and history. | Filter unrestricted repository results as its security mechanism. |
| `evidencelogger.service.auth` | Authentication, password verification, session issuance, and reusable role/assignment authorization checks. | Expose stored hashes or accept an actor ID in place of a session. |
| `evidencelogger.service.casework` | Case creation, assignment changes, storage-location maintenance, evidence registration and voiding, and authorized case/evidence queries. | Commit transactions in repositories. |
| `evidencelogger.service.checkout` | Request, decision, handoff, acknowledgment, note, return, and inspection use cases. | Depend on JavaFX types. |
| `evidencelogger.service.history` | Authorized ordered-history queries and append-only correction use cases. | Update or delete prior history. |
| `evidencelogger.service.dto` | Command inputs and read models returned to UI. Prefer immutable Java records. | Contain JDBC objects or UI controls. |
| `evidencelogger.domain` | Entities/value objects, role and state enums, transition preconditions, and domain-specific failures. | Depend on services, JDBC, JavaFX, or filesystem APIs. |
| `evidencelogger.repository` | Narrow persistence interfaces used by services, grouped by aggregate/use case rather than one generic CRUD repository. | Define business authorization. |
| `evidencelogger.repository.jdbc` | SQL, row mapping, conditional updates/inserts, SQLite constraint translation, and implementations of repository interfaces. | Begin, commit, or roll back a service transaction. |
| `evidencelogger.infrastructure.db` | Connection factory, SQLite connection pragmas, transaction runner, migration runner, and database lifecycle. | Contain role-specific workflow logic. |
| `evidencelogger.infrastructure.security` | JDK-based password hashing and secure comparison. | Log credentials or retain password character arrays. |
| `evidencelogger.infrastructure.logging` | `java.util.logging` configuration, rotating file handler, and safe diagnostic context. | Replace the append-only domain audit trail. |
| `evidencelogger.infrastructure.time` | Injectable `Clock` and ID generator implementations. | Decide domain outcomes. |

Package splits should follow actual size. For example, one `CheckoutService` is preferable to a class per button, while a single all-purpose `EvidenceLoggerService` would violate SRP. Shared validation should live at the lowest appropriate layer: immediate form-quality feedback in UI, authoritative input/state rules in the service/domain layer, and integrity constraints in SQLite.

## 4. Layer boundaries

### UI and controllers

Views are built in Java code as approved. Controllers accept service interfaces and the session holder through constructors. They may perform presentation checks such as required-field highlighting, but service calls repeat all authoritative validation. Role navigation chooses a distinct top-level view after authentication; it is not an authorization boundary.

Controllers send typed command records containing only user-entered business data and target identifiers. They do not send a session, actor, role, event time, resulting state, or audit-event data. Services obtain those values from the service-owned current session, `Clock`, and current database records. Controllers receive purpose-built read models rather than mutable persistence entities.

### Services

Each public service method represents a use case. A command service method performs, in order, session validation, role authorization, a transaction, current-assignment and state checks, state/record changes, and audit-event insertion. Checks whose truth can change, especially assignment and workflow state, occur inside the same write transaction as the change.

Query services authorize before fetching or constrain their SQL by the signed-in Investigator ID. They must not load every case and rely on UI filtering. Custodian and Investigator UI can reuse the same query service when its authorization policy produces the correct result.

Domain failures are typed, for example `Unauthenticated`, `Forbidden`, `ValidationFailure`, `InvalidTransition`, `Conflict`, and `StorageFailure`. The UI maps them to readable messages. Detailed SQL and stack information goes only to diagnostic logging.

### Domain

The domain package represents the confirmed request and custody state machines and validates legal transitions. Domain objects are free of persistence annotations and JavaFX properties. IDs are typed value objects or clearly named immutable values so a case ID cannot be accidentally used as an evidence ID.

Domain logic determines whether a transition is legal; services determine whether the authenticated actor may attempt it and coordinate persistence. Database constraints and conditional SQL provide the final concurrency/integrity guard. This intentional overlap is defense in depth, not duplicated business policy.

### Repositories

Repository interfaces describe business-oriented persistence operations such as loading an item for a transition, finding active requests, appending an event, and listing assigned cases. Avoid a generic `Repository<T>` because it would expose inappropriate update/delete operations and obscure the conditional writes needed for audit and concurrency guarantees.

JDBC repositories accept the transaction's `java.sql.Connection` for command operations. They never change auto-commit, commit, roll back, or close that connection. They use prepared statements exclusively and close statements/result sets with try-with-resources. Query operations may use a connection supplied by a read transaction.

There is no repository method to update or delete an audit event, original examination note, or note correction. Corrections are inserts linked to their target. SQLite foreign keys, uniqueness constraints, check constraints, and conditional updates enforce the confirmed invariants, including one active request and one active handoff/checkout per item.

## 5. Authentication and service-level authorization

`AuthenticationService` looks up a username and verifies a salted password hash. The proposed standard-library scheme is `PBKDF2WithHmacSHA256`, with a unique cryptographically random salt per account. Store the algorithm name, work factor, salt, and derived hash so parameters can be upgraded later. Clear the caller's password `char[]` after verification and never convert it to a long-lived `String`.

On success, `AuthenticationService` installs an immutable `AuthenticatedSession` containing the authenticated user ID and role in a service-owned `SessionManager`. The application permits only one current session; restarting or signing out clears it. Other services obtain the current session from that manager rather than accepting session or actor data from a controller, so the UI cannot retain a signed-out session or nominate a different actor. In-process sessions provide application authorization, not a claim that the local machine or unencrypted database resists an administrator with filesystem access.

Every protected public service method first requires a current session from `SessionManager`. Central helpers implement `requireCustodian`, `requireInvestigator`, `requireAssignedInvestigator`, and `requireCollectingInvestigator`. Helpers return a typed authorization failure and are shared by command and query services. Current assignment is re-read from the database for every Investigator operation, including reads and direct service calls. A role check alone is insufficient.

Authorization tests must call services directly, not only exercise disabled UI controls. Invalid credentials return no session and no protected records. Authentication failures should use a generic message that does not reveal whether a username exists.

## 6. Transaction ownership and concurrency

`TransactionRunner` in `infrastructure.db` owns transaction boundaries. A command service supplies one callback; the runner obtains a connection, disables auto-commit or starts the appropriate SQLite write transaction, runs all repository work, commits once, and rolls back on every exception. It restores connection state and closes the connection. Services and repositories must not nest independent transactions.

One service command equals one transaction. In particular:

- Case creation and the initial assignment insert with both required events.
- Every state change and all of its required audit events commit together.
- Handoff recording and acknowledgment are separate commands and therefore separate transactions/events, as required.
- Corrections insert a new correction and its event without modifying the target.
- Read-modify-write preconditions and current assignment checks occur in the same write transaction.

Configure `PRAGMA foreign_keys=ON` on every connection and a bounded SQLite busy timeout. Use conditional SQL such as an update constrained by the expected prior state and verify exactly one affected row. Use unique/partial indexes for single-active-record invariants where SQLite supports the condition. A zero-row conditional update or uniqueness violation becomes a `Conflict`/`InvalidTransition`, not a retry that records a duplicate action.

The desktop use case expects one application process at a time, but correctness must not rely only on the UI serializing clicks. Database constraints and conditional writes protect against double-clicks, background task overlap, and an accidentally opened second process. Automatic retry of a custody-changing command is not allowed because it could duplicate a physical action; the user can refresh and see the committed current state.

## 7. SQLite schema and migrations

The conceptual schema follows the approved entities: user accounts, cases, assignments, storage locations, evidence items, checkout requests, handoffs/checkouts, examination notes and their corrections, and audit events. The detailed DDL belongs in migration resources, not in controllers or repositories. Internal primary keys and generated public evidence references must be stable and unique. Store event times as UTC instants and present them in the workstation's local zone. History queries order by timestamp and stable event ID as the required tie-breaker.

Use a small in-house migration runner rather than Flyway or Liquibase. Versioned, immutable SQL files live under `src/main/resources/db/migration`, for example `V001__initial_schema.sql`. A `schema_migration` table records version, name, checksum, and applied time.

At startup, before login:

1. Resolve and create the application data directory.
2. Open the database and set connection pragmas.
3. Create/read migration metadata and reject a database whose schema version is newer than the application supports.
4. Verify checksums for already-applied migrations.
5. Apply each pending migration in order and record it in the same transaction where SQLite permits it.
6. Fail startup with a readable message and diagnostic details if a migration cannot complete; never continue against a partly understood schema.

Never edit a released migration; add a new one. Migration integration tests run from an empty database through the latest version and, once a second version exists, from each supported prior version. Destructive downgrade is not supported.

The initial schema setup must seed one fictional Custodian and at least two fictional Investigators, as approved. Only salted derived hashes are stored. Seeding is idempotent and tied to initialization, not normal application startup; account administration remains out of scope. The documented demo plaintext credentials belong only in the User Guide, never in source logging or database columns.

## 8. Local files and attachment handling

The app stores mutable data outside the installation/JAR directory, in a platform-appropriate per-user application-data directory resolved by `app`. The directory contains:

```text
EvidenceLogger/
  evidencelogger.db
  logs/
    evidencelogger-<rotation>.log
```

The exact directory mapping should use the normal per-user location on Windows, macOS, and Linux and must be covered by path-resolution tests. Paths are built with `java.nio.file.Path`; no slash style or writable current directory is assumed.

**Confirmed exclusion:** Evidence attachments are outside the MVP. Therefore the architecture has no attachment table, BLOB column, file-copy service, attachment directory, preview control, or orphan-file cleanup job. A user cannot select or associate a file with evidence. Diagnostic logs are not attachments.

If attachments are approved later, they require a separate specification covering allowed types/sizes, immutable naming, integrity hashes, atomic database/file coordination, viewing safety, correction/deletion/retention rules, and inclusion in backup. Until those decisions are approved, adding a generic file store would create unused complexity and potentially invent evidence-handling rules.

## 9. Backup and restoration

**Confirmed exclusion:** All backup and restore features are outside the MVP. There is no backup/restore UI, service, repository, scheduled copy, export format, or restore-at-startup switch. The User Guide must not imply that copying a live database file is a supported consistent backup, especially if SQLite creates journal or WAL side files.

The application-data path is centralized so a later approved maintenance component can locate the database without changing business services. That is the only MVP accommodation. A future backup/restore specification would need to decide at least application shutdown/exclusive access, SQLite-consistent snapshot method, attachment inclusion if attachments exist, manifest and checksum format, schema-version compatibility, restore validation, atomic replacement, pre-restore recovery copy, retention, and audit expectations. None of those are current behavior.

## 10. Diagnostic logging

Configure `java.util.logging` once during startup. Write timestamped, size-bounded rotating files beneath the application log directory and a concise console stream during development. Record startup/version/platform, migration results, unexpected exceptions, storage failures, rejected invalid operations at an appropriate level, and shutdown. Give each service invocation a generated diagnostic operation ID so a UI error can be correlated without exposing SQL details.

Diagnostic logging and domain audit history have different purposes:

- Diagnostic logs describe software operation and failures and may rotate.
- Audit events are permanent domain records, append-only, transactional, and shown in authorized history views.

Never log plaintext passwords, password hashes/salts, full SQL parameters, examination-note text, request purpose, or correction text. Prefer stable record IDs, action names, error categories, and the authenticated user ID. A failed operation should be logged only after rollback is known; it must not create a domain audit event.

Logging failure should be reported to the user when practical but must not cause a successful custody transaction to be repeated. File-handler initialization should fall back to console diagnostics rather than preventing access to the database unless the team later approves logging as a hard startup requirement.

## 11. Testing strategy

### Unit tests

- Test pure request and custody state transitions, validation, note-freezing, and correction rules without JavaFX or SQLite.
- Test authorization policies for both roles, current assignment, requester/collector identity, note authorship, and forbidden direct service calls.
- Test controller presentation logic only where it has behavior worth preserving; do not duplicate JavaFX framework tests.
- Inject `Clock`, ID generation, repositories, and transaction runner interfaces. Use small hand-written fakes rather than adding a mocking framework for the MVP.
- Test password hashing with fixed test parameters/vectors and separately test that production hashing creates distinct salts.

### SQLite integration tests

Use the real Xerial SQLite driver and a fresh database file under JUnit's temporary directory for each test or test class. Do not substitute H2 or rely on a shared developer database. Cover:

- migration from an empty database, schema checksum/version handling, foreign keys, and demo-account seeding;
- repository mappings and required uniqueness/check constraints;
- assigned-case query isolation and service-level authorization;
- request, handoff, acknowledgment, note, return, inspection, assignment removal, and correction paths;
- duplicate/competing actions and the one-active-request/handoff/checkout constraints;
- history ordering and the absence of update/delete paths for immutable records;
- an injected failure after a state update but before audit insertion, proving both state and event roll back;
- readable translation of locked-database, constraint, and invalid-transition failures.

An integration-test repository decorator that throws after the state write but before the audit insert is preferable to weakening production code with a test-only flag.

### UI, CI, and release checks

Keep automated acceptance coverage mainly at the service/database boundary because that is where authorization and workflow correctness live. Add a small startup/navigation smoke test only if it is stable in headless CI; TestFX is not proposed for the MVP. Manually verify both role views and the complete core flow.

GitHub Actions runs the Gradle wrapper's clean test and release-JAR build on proposed merges. Publish JUnit results and retain the built artifact when practical. Separately smoke-test the actual release JAR on Windows, macOS, and Linux and record which OS/CPU combinations were tested, failed, or skipped in the Developer Guide. Unexecuted platform checks must not be reported as passing.

## 12. Proposed dependencies

Pin exact versions in Gradle dependency declarations and update them deliberately. The architecture needs only:

| Dependency/plugin | Scope | Reason |
| --- | --- | --- |
| Java SE 25 | Runtime/toolchain | Records, standard cryptography, NIO paths, concurrency utilities, and `java.util.logging`; it is the approved runtime prerequisite. |
| OpenJFX `javafx-controls` 25 | Production | Provides JavaFX UI controls; its transitive base/graphics modules support the code-built screens. Declare platform artifacts explicitly for the release JAR. |
| Xerial `sqlite-jdbc` | Production | Approved JDBC driver; embeds SQLite and native libraries for major desktop platforms. |
| JUnit Jupiter | Test | Approved unit and integration test engine and assertions. |
| Gradle Wrapper 9.1+ | Build | Reproducible approved build entry point. |
| Gradle `application` plugin | Build, bundled with Gradle | Defines the launcher/main class and run task without another external plugin. |
| OpenJFX Gradle plugin | Build | Simplifies JavaFX module/classpath setup for development and tests. Its single-platform default must not be mistaken for the all-platform release artifact. |

The exact Xerial, JUnit, and build-plugin patch versions are implementation-time dependency pins, not product rules. No additional runtime framework is proposed. In particular, do not add Spring, Guice, Hibernate/JPA, Flyway/Liquibase, SLF4J/Logback, a connection pool, JSON library, or filesystem abstraction. Do not add Mockito or TestFX unless a concrete test becomes materially clearer and the dependency is separately justified.

The all-platform release JAR can be assembled by an explicit Gradle task that includes application classes, runtime dependencies, and the selected JavaFX platform artifacts while preserving required service metadata. This avoids adding a packaging framework, but the task must be verified by launching the produced artifact rather than merely checking that it exists.

## 13. Cross-platform release and packaging risks

| Risk | Mitigation/check |
| --- | --- |
| The JavaFX Gradle plugin normally resolves the build host's platform only. | Declare every supported JavaFX OS/CPU artifact explicitly in the release configuration and inspect the JAR contents. Do not call a host-only JAR cross-platform. |
| JavaFX and SQLite include native libraries whose availability varies by OS and CPU. | Define the supported OS/architecture matrix, build once only if the artifact truly contains all entries, and launch-test every claimed target. At minimum, report untested architectures honestly. |
| JavaFX native entries or `META-INF/services` files can be lost/overwritten while building a fat JAR. | Configure duplicate/service-resource handling deliberately and test JavaFX startup plus a real SQLite connection from the final JAR. |
| The fat JAR does not contain a Java 25 runtime. | State Java 25 clearly as a prerequisite and fail startup/readme checks clearly when the runtime is unsuitable. Do not describe the JAR as a native installer. |
| A JAR that launches from Gradle may fail with `java -jar`. | Use the separate non-`Application` launcher, set the manifest main class, and smoke-test exactly `java -jar <artifact>`. |
| OS security controls may warn about unsigned/unnotarized downloads. | Document this limitation for the MVP. Code signing, notarization, native installers, and automatic updates require separate release scope. |
| Installation directories may be read-only and path conventions differ. | Store the database/logs in the per-user application-data directory, use NIO paths, and test spaces/non-ASCII characters in paths. |
| Native support can differ between x86-64 and ARM64, especially on macOS. | Do not infer CPU support from an OS-level test. Record the exact runtime, OS, and architecture used in each smoke test. |
| SQLite locking behavior can differ on network/synchronized folders. | Support only a local per-user filesystem for the MVP; show a clear storage error and do not advertise network-share/cloud-sync use. |

The approved specification names Windows, macOS, and Linux but does not define a CPU-architecture support matrix. Before calling a release universally cross-platform, the team must state which combinations it claims (for example Windows x86-64, Linux x86-64, and macOS x86-64/ARM64) and either test each or mark it untested.

## 14. Conflicts and open decisions

### Conflicts with approved specifications

1. The requested architecture topics include **attachment handling**, but `specs/product.md` explicitly excludes attachments from the MVP. This proposal resolves the conflict in favor of the approved specification: it documents that there is no attachment capability and adds no attachment architecture.
2. The requested architecture topics include **backup and restoration**, but both approved specifications explicitly keep all backup and restore features outside the MVP. This proposal resolves the conflict in favor of the approved specifications: it documents the exclusion and only centralizes the database path, which is already needed for normal operation.
3. “Local file storage” could imply evidence-file storage, but the approved MVP persists structured application data in SQLite and diagnostics in local log files. It does not authorize evidence files. This proposal uses local files only for the SQLite database and diagnostic logs.

Implementing attachments or backup/restore would require an explicit approved scope change and new domain/security rules; this architecture document alone does not authorize either feature.

### Remaining decisions before implementation/release

- Choose the organization-qualified Java package/application identifier and resulting platform data-directory name. This does not alter domain behavior.
- Pin exact patch versions of Xerial SQLite JDBC, JUnit Jupiter, and the OpenJFX Gradle plugin after compatibility verification with Java/JavaFX 25 and Gradle 9.1+.
- Define the release OS/CPU support matrix. The approved OS list alone is not precise enough to claim support for every architecture.
- Select and record the PBKDF2 work factor through a startup/login performance check on the slowest supported machine; the stored algorithm/work-factor fields allow later adjustment without changing business rules.
- If the request to “cover” attachments or backup/restore was intended to place those features inside the MVP, the approved product and domain specifications must first be amended. No behavior for them should be inferred here.
