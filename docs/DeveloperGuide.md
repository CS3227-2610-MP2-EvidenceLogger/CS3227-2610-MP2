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

From the repository root, run the application on Windows with:

```powershell
.\gradlew.bat run
```

On macOS or Linux, use:

```bash
./gradlew run
```

Run the required automated checks with `gradlew.bat clean check` on Windows or
`./gradlew clean check` on macOS/Linux. Build the release artifact with `shadowJar`; the output is
`build/libs/EvidenceLogger.jar`.

The `releaseSmokeTest` Gradle task builds that JAR and launches the exact artifact with
`java -jar ... --smoke-test`. The smoke path initializes the real application composition,
migrates a temporary SQLite database, and exits without starting JavaFX. It verifies packaged
class/resource discovery, the manifest entry point, migrations, and SQLite native loading; it is
not a substitute for an interactive UI smoke test.

## Architecture

The application uses explicit constructor wiring and five principal areas:

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
Database calls are dispatched through the application's single background executor and results are
rendered on the JavaFX Application Thread.

## Local data and diagnostics

`ApplicationDataPaths` selects a per-user `EvidenceLogger` directory:

| Platform | Primary directory | Fallback |
| --- | --- | --- |
| Windows | `%LOCALAPPDATA%\EvidenceLogger` | `%USERPROFILE%\AppData\Local\EvidenceLogger` |
| macOS | `~/Library/Application Support/EvidenceLogger` | Same location |
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

JUnit Jupiter tests are under `src/test/java`. The suite includes pure domain/controller tests,
real-SQLite repository and migration integration tests, direct-service authorization tests,
transaction rollback tests, and composition-level workflow tests. JavaFX presentation behavior is
tested at controller and presentation-model boundaries; TestFX is not configured.

Useful focused commands include:

```powershell
.\gradlew.bat test --tests 'evidencelogger.ui.investigator.*'
.\gradlew.bat test --tests 'evidencelogger.service.casework.DefaultCaseworkServiceIntegrationTest'
.\gradlew.bat test --tests 'evidencelogger.app.ApplicationDataPathsTest'
```

JaCoCo HTML and XML reports are generated after tests. Coverage is navigation evidence, not the
acceptance criterion; behavioral assertions and the required `clean check` remain authoritative.

## CI and release verification

`.github/workflows/gradle-automate-jar.yml` is configured for pull requests to `master` on GitHub's
Windows, macOS, and Ubuntu runners. Each matrix job runs `clean check releaseSmokeTest` and uploads
its own `EvidenceLogger.jar`. A configured matrix is not proof of a passing run; consult the actual
workflow run before making a release claim.

Verification recorded for this checkout on 27 September 2026:

| Check | Environment | Result |
| --- | --- | --- |
| Focused Investigator presentation/controller tests | Local Windows, Java 25 | Passed after correcting outdated UTC-based expectations. |
| Focused Custodian presentation and casework service tests | Local Windows, Java 25, real temporary SQLite | Passed. |
| Platform path resolver tests | Local Windows, Java 25 | Passed for deterministic Windows, macOS, Linux, and fallback inputs. |
| `releaseSmokeTest` against `build/libs/EvidenceLogger.jar` | Local Windows, Java 25, real temporary SQLite | Passed. |
| `gradlew.bat clean check` | Local Windows, Java 25 | Passed; 186 tests, zero failures, errors, or skips, plus both Checkstyle tasks and JaCoCo reporting. |

No credential-driven JavaFX workflow was manually exercised during this change. Actual macOS and
Linux workflow results, interactive release-JAR launch, and non-host CPU architectures remain
unverified until their corresponding CI/manual checks are observed and recorded.

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
