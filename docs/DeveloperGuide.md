# EvidenceLogger Developer Guide

This guide describes the current EvidenceLogger checkout for contributors. EvidenceLogger is an
offline JavaFX desktop application for fictional evidence-custody workflows; it is not a legal,
police, or forensic system.

## Prerequisites

- A Java 25 JDK. The Gradle toolchain and release artifact both require Java 25.
- The checked-in Gradle wrapper. No system Gradle installation is required.
- A graphical desktop for interactive JavaFX use. Automated service, persistence, and release
  smoke checks do not open the sign-in screen.

Do not enter real evidence or credentials into development databases, tests, logs, or issue
reports.

## Build and run

Use the checked-in wrapper from the repository root. If Gradle cannot write to its default user
home, set `GRADLE_USER_HOME` to a writable directory outside the repository before running the
wrapper.

| Purpose | Windows | macOS/Linux |
| --- | --- | --- |
| Run the application | `.\gradlew.bat run` | `./gradlew run` |
| Run the required clean build and checks | `.\gradlew.bat clean check` | `./gradlew clean check` |
| Generate the configured HTML and XML coverage reports | `.\gradlew.bat coverage` | `./gradlew coverage` |
| Build the release JAR | `.\gradlew.bat shadowJar` | `./gradlew shadowJar` |
| Build and smoke-test the release JAR | `.\gradlew.bat releaseSmokeTest` | `./gradlew releaseSmokeTest` |

For example, run the application on Windows with:

```powershell
.\gradlew.bat run
```

On macOS or Linux, use:

```bash
./gradlew run
```

`shadowJar` writes `build/libs/EvidenceLogger.jar`. The wrapper is pinned to Gradle 9.1.0, and the
Java toolchain is pinned to Java 25.

The `releaseSmokeTest` Gradle task builds that JAR and launches the exact artifact with
`java -jar ... --smoke-test`. The smoke path initializes the real application composition,
migrates a temporary SQLite database, and exits without starting JavaFX. It verifies packaged
class/resource discovery, the manifest entry point, migrations, and SQLite native loading; it is
not a substitute for an interactive UI smoke test.

## Architecture

The current dependency direction is:

```text
JavaFX views/controllers
        |
        v
services + authorization ---> domain rules and typed IDs
        |
        v
repository contracts
        |
        v
JDBC repositories + transaction/migration infrastructure
        |
        v
local SQLite database
```

`EvidenceLoggerApplication` owns the JavaFX lifecycle and the single database executor.
`ApplicationComposition` is the composition root: it runs migrations first and then constructs the
shared session, services, repositories, transaction runner, clock, and ID generators. The
application uses five principal areas:

| Area | Current responsibility |
| --- | --- |
| `evidencelogger.ui` | Separate Custodian and Investigator JavaFX views, controllers, validation presentation, and shared UI formatters. |
| `evidencelogger.service` | Authentication, authorization, casework, checkout workflow, and append-only history use cases. |
| `evidencelogger.repository` | Narrow persistence contracts and plain-JDBC SQLite implementations. |
| `evidencelogger.infrastructure` | Connection setup, transaction ownership, migrations, clocks/IDs, and diagnostic logging. |
| `evidencelogger.app` | JavaFX lifecycle, role routing, application-data paths, and composition-root wiring. |

UI controls are not an authorization boundary. Protected services obtain the active actor from the
service-owned session and enforce role and current-assignment rules. `JdbcTransactionRunner` owns
command transactions; repositories do not commit or roll back. Required state changes and their
audit events are written in the same transaction. History and corrections are append-only.

The role workspaces share service interfaces and read models, but retain distinct top-level views.
Database calls are dispatched through the application's single daemon executor and results are
rendered on the JavaFX Application Thread. On shutdown, the application clears the session, waits
up to five seconds for the executor, and closes its diagnostic file handler.

## Local data and diagnostics

`ApplicationDataPaths` selects and normalizes a per-user `EvidenceLogger` directory:

| Platform | When platform setting exists | Fallback |
| --- | --- | --- |
| Windows | `%LOCALAPPDATA%\EvidenceLogger` | `%USERPROFILE%\AppData\Local\EvidenceLogger` |
| macOS | Not applicable | `~/Library/Application Support/EvidenceLogger` |
| Linux/other Unix | `$XDG_DATA_HOME/EvidenceLogger` | `~/.local/share/EvidenceLogger` |

The directory contains `evidence-logger.db` and a `logs` directory. Diagnostic logging writes up to
five rotating `evidencelogger-N.log` files of approximately 1 MiB each. Diagnostic logs are
separate from append-only custody history and must not contain passwords or sensitive free text.
If file logging cannot be created, the application retains console logging.

Do not edit the SQLite database manually. Backup and restore are outside the MVP; copying a live
database is not documented as a supported backup procedure.

## Database and migrations

SQLite connections are opened through `SqliteConnectionFactory`, which enables foreign keys and a
bounded busy timeout for every connection. `MigrationRunner` applies the ordered resources listed
in `src/main/resources/db/migration/migrations.list` and verifies recorded checksums. Released
migrations are immutable; add a next-ordered migration for any approved schema change.

The current migration chain is:

1. `V001__initial_schema.sql`
2. `V002__align_checkout_persistence.sql`
3. `V003__add_voided_evidence.sql`

Persistence tests use the real Xerial SQLite driver and isolated temporary database files.

## Testing

JUnit Jupiter tests are under `src/test/java` and assertions are enabled for every Gradle `Test`
task. The principal behavioral boundaries are:

| Boundary | What the current suite proves |
| --- | --- |
| Domain and DTO | Legal custody transitions and immutable command/view contracts. |
| Service | Authentication/authorization, casework, checkout, history, and rollback behavior through direct service calls. |
| SQLite integration | Connection pragmas, migration upgrades, repository mappings/constraints, transaction behavior, and atomic audit persistence against real temporary databases. |
| Presentation | Login, role routing, controllers, view presentation models, formatting, and selection styles without a TestFX-driven interactive desktop. |
| Composition | The production object graph can execute database-backed workflows and shut down deterministically. |

Useful focused commands include:

```powershell
.\gradlew.bat test --tests 'evidencelogger.ui.investigator.*'
.\gradlew.bat test --tests 'evidencelogger.service.casework.DefaultCaseworkServiceIntegrationTest'
.\gradlew.bat test --tests 'evidencelogger.app.ApplicationDataPathsTest'
```

`test` is finalized by `jacocoTestReport`, so `clean check` generates the HTML report at
`build/reports/jacoco/test/html/index.html`. The separate `coverage` task is configured to generate
both HTML and XML. Coverage is navigation evidence, not the acceptance criterion; behavioral
assertions and the required `clean check` remain authoritative.

## CI and release verification

`.github/workflows/gradle-automate-jar.yml` is configured for pull requests to `master` on GitHub's
Windows, macOS, and Ubuntu runners. Each matrix job runs `clean check releaseSmokeTest` and uploads
its own `EvidenceLogger.jar`. A configured matrix is not proof of a passing run; consult the actual
workflow run before making a release claim.

### Automated results for this checkout

Recorded on 28 September 2026 against base commit
`068e1c977450fb05b75ef4cdef23c93acf8bdaba` plus this documentation change, on Windows 11 10.0
amd64, using Temurin OpenJDK 25.0.3 and Gradle 9.1.0:

| Command | Exit | Observed result |
| --- | --- | --- |
| `$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'evidencelogger-gradle-home'; .\gradlew.bat tasks --all` | 0 | Confirmed the documented `run`, `clean`, `check`, `coverage`, `shadowJar`, and `releaseSmokeTest` tasks. |
| `$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'evidencelogger-gradle-home'; .\gradlew.bat clean check` | 0 | Passed compilation, 35 JUnit suites containing 186 tests, zero failures/errors/skips, `checkstyleMain`, `checkstyleTest`, and HTML JaCoCo report generation. |
| `$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'evidencelogger-gradle-home'; .\gradlew.bat coverage` | 0 | Generated `build/reports/jacoco/coverage/coverage.xml` and the configured HTML coverage report; Windows registry preference warnings did not fail the task. |
| `$env:GRADLE_USER_HOME = Join-Path $env:TEMP 'evidencelogger-gradle-home'; .\gradlew.bat releaseSmokeTest` | 0 | Built `build/libs/EvidenceLogger.jar`; the packaged entry point, composition, migration resources, temporary SQLite database, and native driver load completed successfully. |

The successful test run emitted a Java 25 warning that `sqlite-jdbc` called the restricted native
loading API without `--enable-native-access=ALL-UNNAMED`. This is currently a warning, not a test
failure, but a future Java release may block the call.

The first task-list attempt failed before Gradle execution because the environment resolved
`GRADLE_USER_HOME` to unwritable `C:\.gradle`. Re-running with the task-specific writable directory
shown above succeeded.

### Manual and unverified results

No credential-driven JavaFX workflow or interactive release-JAR launch was manually exercised for
this guide. MarkBind 6.0.2 is configured in the documentation workflow, but the CLI is not installed
in this local environment, so the rendered documentation site was not built here. Actual GitHub
Actions results, macOS and Linux execution, and non-amd64 architectures remain unverified until
their corresponding CI or manual checks are observed and recorded.

## Troubleshooting

- **Gradle cannot create its cache or lock file:** ensure `GRADLE_USER_HOME` points to a writable
  user directory and retry the wrapper command. Do not place mutable application data in the
  repository to work around a cache problem.
- **Startup reports that local data could not be prepared:** verify the platform data directory is
  writable and inspect the console or rotating diagnostic log. Preserve any displayed diagnostic
  reference.
- **A storage operation fails:** do not edit database rows. Capture the user-safe message,
  diagnostic reference, operating system, Java version, and the action attempted.
- **The release JAR builds but may not launch:** run `releaseSmokeTest` first, then perform the
  interactive JavaFX smoke check on each claimed OS/CPU combination. Record failures and skipped
  platforms rather than inferring support from a successful build.
