# EvidenceLogger architecture

This document describes the current implementation and the intended architecture approved in `specs/architecture.md`, `specs/product.md`, and `specs/domain-rules.md`. Solid boxes in the diagram have concrete implementation in `src`; dashed boxes remain planned or only partially implemented.

## Architecture diagram

```mermaid
flowchart TB
    classDef present fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef planned fill:#fff8e1,stroke:#ef6c00,color:#7f3f00,stroke-dasharray: 5 5
    classDef external fill:#eceff1,stroke:#455a64,color:#263238

    subgraph UI[JavaFX presentation]
        APP[evidencelogger.app\nApplication + Launcher]:::present
        SHELL[evidencelogger.ui.common\nApplicationShell]:::present
        LOGIN[evidencelogger.ui.login\nLogin view/controller]:::present
        CUST[evidencelogger.ui.custodian\nCustodian casework/workflow UI]:::present
        INV[evidencelogger.ui.investigator\nInvestigator workspace]:::present
        SESSION[evidencelogger.app.session\nNavigation/session observation]:::planned
    end

    subgraph SERVICES[Application services]
        AUTH[evidencelogger.service.auth\nAuthentication, session, authorization]:::present
        CASE[evidencelogger.service.casework\nCases, assignments, locations, evidence]:::present
        CHECK[evidencelogger.service.checkout\nCheckout commands and queries]:::present
        HIST[evidencelogger.service.history\nAudit writing and history]:::present
        DTO[evidencelogger.service.dto\nCommands and read models]:::present
        ERR[evidencelogger.service\nTyped service failures]:::present
    end

    DOMAIN[evidencelogger.domain\nTyped IDs, roles, states, domain rules]:::present

    subgraph PERSISTENCE[Persistence boundary]
        REPO[evidencelogger.repository\nBusiness-oriented repository contracts]:::present
        JDBC[evidencelogger.repository.jdbc\nJDBC SQL, mapping, constraint translation]:::present
    end

    subgraph INFRA[Infrastructure]
        DB[evidencelogger.infrastructure.db\nConnections, migrations, transactions]:::present
        SEC[evidencelogger.infrastructure.security\nPassword hashing/comparison]:::present
        LOG[evidencelogger.infrastructure.logging\njava.util.logging diagnostics]:::present
        TIME[evidencelogger.infrastructure.time\nClock and ID generation]:::present
    end

    SQLITE[(Local SQLite database)]:::external
    FILES[(Per-user data directory\nDB + rotating logs)]:::external

    APP --> SHELL
    APP --> LOGIN
    APP --> DB
    APP --> LOG
    SESSION --> AUTH
    LOGIN --> AUTH
    CUST --> CASE
    CUST --> CHECK
    CUST --> HIST
    INV --> CASE
    INV --> CHECK
    INV --> HIST
    SHELL --> SESSION
    CASE --> AUTH
    CHECK --> AUTH
    HIST --> AUTH
    CASE --> DTO
    CHECK --> DTO
    HIST --> DTO
    AUTH --> DOMAIN
    CASE --> DOMAIN
    CHECK --> DOMAIN
    HIST --> DOMAIN
    CASE --> REPO
    CHECK --> REPO
    HIST --> REPO
    CHECK --> DB
    CASE --> DB
    HIST --> DB
    REPO --> JDBC
    JDBC --> DB
    DB --> SQLITE
    DB --> FILES
    LOG --> FILES
    AUTH --> SEC
    CASE --> TIME
    CHECK --> TIME
    HIST --> TIME
```

Legend: green solid nodes are represented by concrete source files today; amber dashed nodes are package-level responsibilities specified but not yet implemented. The diagram shows intended dependency direction, not a claim that every edge is currently wired.

## Package responsibilities

| Package | Current contents | Intended responsibility and boundary |
| --- | --- | --- |
| `evidencelogger.app` | `EvidenceLoggerApplication`, `EvidenceLoggerLauncher` | JavaFX lifecycle, object-graph composition, startup/shutdown, application-data path, migrations, and executor ownership. It must not contain workflow rules or SQL. |
| `evidencelogger.app.session` | Package marker only | Observe authentication state and drive role navigation. It must not decide case access or accept a UI-supplied actor identity. |
| `evidencelogger.ui.common` | Application shell, workspace header, history formatting, and service-failure presentation | Shared code-built controls, presentation helpers, navigation shell, and user-readable error presentation. It must not authorize or mutate domain state. |
| `evidencelogger.ui.login` | `LoginView`, `LoginController` | Sign-in view/controller using the authentication service; no direct database access. |
| `evidencelogger.ui.custodian` | Casework and checkout workflow views/controllers | Custodian cases, assignments, locations, registration, search, request decisions, handoffs, return inspection, and history. |
| `evidencelogger.ui.investigator` | Investigator workspace view/controller | Assigned-case search, requests, acknowledgment, notes, returns, and history. UI filtering is not the security boundary. |
| `evidencelogger.service` | `ServiceException` | Persistence-neutral failures translated to readable UI messages: unauthenticated, forbidden, validation, invalid transition, conflict, not found, and storage failure. |
| `evidencelogger.service.auth` | Authentication, session, and authorization services | Password verification coordination, service-owned session issuance, role checks, and current-assignment checks. |
| `evidencelogger.service.casework` | Authorized command/query service | Case creation, assignments, storage locations, evidence registration, and authorized case/evidence queries. |
| `evidencelogger.service.checkout` | Authorized command and query implementations | Request, decision, handoff, acknowledgment, note, return, inspection, and correction workflows and reads. |
| `evidencelogger.service.history` | Audit writing and authorized query implementation | Authorized ordered history and append-only corrections; audit writes use the caller-owned transaction connection. |
| `evidencelogger.service.dto` | Checkout command records and read models | Immutable, technology-neutral inputs/outputs between UI and services. No JavaFX or JDBC types. |
| `evidencelogger.domain` | Typed IDs, role/state enums | Persistence/UI-independent values and state rules for request and custody transitions. |
| `evidencelogger.repository` | Casework, checkout, authorization, and credential contracts | Narrow, business-oriented persistence contracts. No authorization or generic CRUD surface. |
| `evidencelogger.repository.jdbc` | Concrete JDBC repositories and audit writer | Prepared SQL, row mapping, conditional updates/inserts, and SQLite constraint translation. It does not commit or roll back. |
| `evidencelogger.infrastructure.db` | SQLite connection, migrations, and transaction runner | Connection pragmas, ordered migrations, and transaction boundaries. One service command equals one transaction. |
| `evidencelogger.infrastructure.security` | `Pbkdf2PasswordVerifier` | JDK-based salted password comparison; no credential logging. |
| `evidencelogger.infrastructure.logging` | `DiagnosticLogging` | Safe rotating diagnostic logs, separate from permanent append-only domain audit history. |
| `evidencelogger.infrastructure.time` | `IdGenerator` contract | Injectable clock and ID generation for production and deterministic tests; it does not decide domain outcomes. |

## Key architectural rules

- Dependencies point inward: UI → services → domain/repository contracts → JDBC/infrastructure → SQLite.
- Services enforce role and current assignment on direct calls; role-specific UI visibility is only presentation.
- Controllers pass typed command data. The service obtains actor, role, current state, and time from its session/database/infrastructure dependencies.
- Each successful custody change and its audit event(s) commit atomically. History and corrections are append-only.
- Approval does not check evidence out; handoff plus Investigator acknowledgment does. Custodian inspection is required before a return becomes available in storage.
- Attachments, backup/restore, case closure, release from hold, account administration, and other deferred features have no package or database component in the MVP.

## Current implementation status

Implemented today: JavaFX startup and login, separate Custodian and Investigator workspaces,
role-based navigation and logout, Custodian casework and checkout workflow screens, Investigator
request/collection/note/return screens, authorized ordered history and append-only documentary
corrections, authentication and session-backed authorization, casework and checkout services, JDBC
repositories, SQLite migrations and transactions, PBKDF2 password verification, rotating
diagnostic logging, typed domain/service contracts, and transactional audit-event writing.
