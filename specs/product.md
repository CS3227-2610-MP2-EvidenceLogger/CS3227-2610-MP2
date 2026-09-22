# EvidenceLogger — product specification

## 1. Product and scope

EvidenceLogger is an offline Java desktop application for a small office to record physical evidence, requests to take it from storage, handoffs, examinations, returns, and custody history. An **Evidence Custodian** controls registration and handoffs. An **Investigator** works only on assigned cases. Both use the same local workstation at different times. The product does not claim legal, police, or forensic certification.

The one-week MVP uses simple lists and forms. Its complete flow is:

`Register in storage → request checkout → approve or reject → handoff and acknowledge collection → add examination notes → initiate return → inspect → store or hold for review`

Complete and test this core flow first. Backup and restore are outside the project scope so the team can focus on completing and verifying the core workflow within one week.

A **checkout request** is an Investigator's request for permission to take one evidence item out of storage for a stated purpose until an expected return time. It is a request record, **not** the handoff. Approval grants permission but leaves the item in storage. At physical collection, the Custodian records the handoff and the Investigator acknowledges receipt; only then does the item become checked out. A rejected request cannot be collected. An item already checked out cannot be checked out again.

## 2. Roles and MVP behavior

| Role | Required MVP tasks |
| --- | --- |
| Evidence Custodian | Sign in; create cases and assign Investigators; register evidence with a generated unique reference, short description, and location selected from a small Custodian-maintained list; approve or reject checkout requests; record handoffs; inspect returns and put clean items back in storage or hold problematic items for review; read custody history. |
| Investigator | Sign in; view and search assigned cases and their evidence; submit checkout requests with purpose and expected return time; view request status; acknowledge collection; add examination notes to that checkout; initiate returns; read history for assigned cases. |

Separate role views must be backed by service-layer authorization. Investigators cannot approve requests, register or relocate evidence, or edit history. They cannot access unassigned cases. The application records the active actor and time for custody-changing actions. Registration, decisions, handoffs, returns, and corrections create append-only history entries. A correction adds a new entry with a reason; previous entries remain unchanged. Notes belong to one checkout, and corrections to notes are appended. A return requires Custodian inspection before storage becomes available again.

### Minimal workflow acceptance checks

1. A Custodian and an Investigator sign in to distinct views. Invalid credentials reveal no records, and a direct call to a protected service by the wrong role is rejected.
2. A Custodian creates a case, assigns an Investigator, and registers two evidence items with distinct generated references and selected locations. Only assigned Investigators can see and search them.
3. An assigned Investigator requests one item. A Custodian can approve or reject requests. Approval alone does not check out the item; a rejected request cannot be collected.
4. Handoff plus Investigator acknowledgment records who gave and received the item and when. The item then shows as checked out, and another checkout attempt fails.
5. The collecting Investigator adds a note and initiates return. The Custodian records inspection; the item shows either in storage or held for review.
6. Authorized users can read ordered history. Attempts to edit or delete an old entry fail; a correction appears as a new entry.

## 3. Confirmed decisions

The team confirmed D-01 through D-07. The final column records whether any detail remains unresolved.

| ID | Confirmed decision | Remaining open detail |
| --- | --- | --- |
| D-01 | When creating an empty local database, seed one fictional Custodian account and at least two fictional Investigator accounts so assigned-case access can be tested. Store salted password hashes, never plaintext. Document the credentials in the User Guide and label them demo-only. Account administration is outside this MVP. | None. |
| D-02 | Investigators see only assigned cases. The Custodian may change a case assignment after creation, and the application records the assignment change in history. | None. |
| D-03 | Evidence registration has exactly three user-supplied fields: case, short description, and Custodian-selected location from a small managed list. The application generates a stable unique reference; its presentation format is an implementation detail. No additional evidence fields are included in the MVP. The Custodian may add locations but may not rename or delete a location after it has been used. | None. |
| D-04 | Approval and physical handoff are separate; collection includes Investigator acknowledgment. Requests do not expire automatically. An Investigator may withdraw a request only while it is pending. Each evidence item may have at most one pending or approved request. A fresh request is permitted whenever the evidence is `IN_STORAGE` and has no pending or approved request; prior terminal requests remain in history and do not block it. | None. |
| D-05 | Notes attach to a specific checkout, require only note text, and are visible to the Custodian and Investigators assigned to the case. Corrections are appended. | None. |
| D-06 | Every return requires Custodian inspection. The only inspection outcomes are `STORED` and `HELD_FOR_REVIEW`; `HELD_FOR_REVIEW` requires a comment. Handling a held item after review is outside this MVP. | None. |
| D-07 | History is append-only; errors are corrected with an entry containing actor, time, and reason. Request rejection appears in the same case/evidence history view. The minimum event fields are defined in `specs/domain-rules.md`. | None. |

## 4. Approved implementation stack

**Approved stack:** Java SE 25; JavaFX 25 with code-built controls and minimal screens; Gradle 9.1 or newer through its wrapper; SQLite with Xerial SQLite JDBC; plain JDBC repositories and service classes; JUnit Jupiter for service and database tests; GitHub Actions for build and test; Java's standard logging API for local diagnostics. Use one local database and no server or web framework. This keeps the architecture small while separating UI, authorization and workflow services, and persistence.

The version and packaging choices have specific support: [JavaFX 25 runs on JDK 23 or later](https://openjfx.io/highlights/25/); [Gradle 9.1 supports running on Java 25](https://docs.gradle.org/current/userguide/compatibility.html); [SQLite JDBC bundles native libraries for major desktop operating systems](https://github.com/xerial/sqlite-jdbc); and [OpenJFX documents cross-platform fat-JAR packaging with platform-specific JavaFX dependencies](https://openjfx.io/openjfx-docs/modular). The standard [JavaFX Gradle plugin normally selects one platform](https://github.com/openjfx/javafx-gradle-plugin/blob/master/README.md), so the team must build and test the all-platform JAR explicitly. Use a launcher class as described by OpenJFX. A Java 25 runtime is a stated installation prerequisite; including JavaFX libraries in the JAR does not include the JDK.

## 5. Reliability and release checks

The following are MVP engineering checks. Record actual test results and any untested platform in the Developer Guide.

- All normal workflows work offline after installation. Stored passwords are salted hashes. No diagnostic log includes passwords.
- Each custody state change and its history entry commit in one database transaction. Test at least one injected failure that rolls both back. Invalid input and storage failures show a readable error without leaving a partial custody change.
- Test service authorization, assigned-case access, request and return transitions, and append-only corrections.
- Run automated tests and a JAR build in CI for proposed merges. Keep timestamped local diagnostic logs for errors and failed operations. Smoke-test the release JAR on Windows, macOS, and Linux; report failures or missing tests honestly.
- Keep the User Guide aligned with the actual release and provide clear setup and fictional demonstration data.

## 6. Deferred features

The MVP excludes real evidence data, legal compliance claims, attachments, derived-evidence links, case closure and disposal, reports and exports, all backup and restore features, overdue alerts, account administration, cloud or multi-computer sync, external integrations, and advanced security features such as encryption or biometrics. Add any of these only after the core flow and mandatory deliverables are complete and verified.
