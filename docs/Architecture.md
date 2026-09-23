# EvidenceLogger architecture

This document describes the current implementation and the intended architecture approved in `specs/architecture.md`, `specs/product.md`, and `specs/domain-rules.md`. The repository is currently a scaffold: the solid boxes in the diagram exist in `src`; dashed boxes are planned package responsibilities with no concrete implementation yet.

## Architecture diagram

```mermaid
flowchart TB
    classDef present fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef planned fill:#fff8e1,stroke:#ef6c00,color:#7f3f00,stroke-dasharray: 5 5
    classDef external fill:#eceff1,stroke:#455a64,color:#263238

    subgraph UI[JavaFX presentation]
        APP[evidencelogger.app\nApplication + Launcher]:::present
        SHELL[evidencelogger.ui.common\nApplicationShell]:::present
        LOGIN[evidencelogger.ui.login\nLogin views/controllers]:::planned
        CUST[evidencelogger.ui.custodian\nCustodian views/controllers]:::planned
        INV[evidencelogger.ui.investigator\nInvestigator views/controllers]:::planned
        SESSION[evidencelogger.app.session\nNavigation/session observation]:::planned
    end

    subgraph SERVICES[Application services]
        AUTH[evidencelogger.service.auth\nAuthentication, session, authorization]:::present
        CASE[evidencelogger.service.casework\nCases, assignments, locations, evidence]:::planned
        CHECK[evidencelogger.service.checkout\nCheckout commands and queries]:::present
        HIST[evidencelogger.service.history\nAudit writing and history]:::present
        DTO[evidencelogger.service.dto\nCommands and read models]:::present
        ERR[evidencelogger.service\nTyped service failures]:::present
    end

    DOMAIN[evidencelogger.domain\nTyped IDs, roles, states, domain rules]:::present

    subgraph PERSISTENCE[Persistence boundary]
        REPO[evidencelogger.repository\nBusiness-oriented repository contracts]:::planned
        JDBC[evidencelogger.repository.jdbc\nJDBC SQL, mapping, constraint translation]:::planned
    end

    subgraph INFRA[Infrastructure]
        DB[evidencelogger.infrastructure.db\nConnections, migrations, transactions]:::present
        SEC[evidencelogger.infrastructure.security\nPassword hashing/comparison]:::planned
        LOG[evidencelogger.infrastructure.logging\njava.util.logging diagnostics]:::planned
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
| `evidencelogger.ui.common` | `ApplicationShell` | Shared code-built controls, async task support, validation display, navigation shell, and user-readable error presentation. It must not authorize or mutate domain state. |
| `evidencelogger.ui.login` | Package marker only | Sign-in view/controller using service contracts; no direct database access. |
| `evidencelogger.ui.custodian` | Package marker only | Custodian workflows: cases, assignments, locations, registration, request decisions, handoffs, returns, and history. It delegates rules to services. |
| `evidencelogger.ui.investigator` | Package marker only | Assigned-case search, requests, acknowledgment, notes, returns, and history. UI filtering is not the security boundary. |
| `evidencelogger.service` | `ServiceException` | Persistence-neutral failures translated to readable UI messages: unauthenticated, forbidden, validation, invalid transition, conflict, not found, and storage failure. |
| `evidencelogger.service.auth` | Session and authorization contracts/value record | Authentication, password verification coordination, service-owned session issuance, role checks, and current-assignment checks. |
| `evidencelogger.service.casework` | Package marker only | Case creation, assignments, storage locations, evidence registration, and authorized case/evidence queries. |
| `evidencelogger.service.checkout` | Command/query interfaces | Request, decision, handoff, acknowledgment, note, return, inspection, and correction use cases. Implementations own use-case coordination, not JDBC transaction mechanics. |
| `evidencelogger.service.history` | Audit draft and append writer contract | Authorized ordered history and append-only corrections; audit writes use the caller-owned transaction connection. |
| `evidencelogger.service.dto` | Checkout command records and read models | Immutable, technology-neutral inputs/outputs between UI and services. No JavaFX or JDBC types. |
| `evidencelogger.domain` | Typed IDs, role/state enums | Persistence/UI-independent values and state rules for request and custody transitions. |
| `evidencelogger.repository` | Package marker only | Narrow, business-oriented persistence contracts. No authorization or generic CRUD surface. |
| `evidencelogger.repository.jdbc` | Package marker only | Prepared SQL, row mapping, conditional updates/inserts, and SQLite constraint translation. It must not commit or roll back. |
| `evidencelogger.infrastructure.db` | `TransactionRunner` contract | Connection factory, SQLite pragmas, migrations, and transaction boundaries. One service command equals one transaction. |
| `evidencelogger.infrastructure.security` | Package marker only | JDK-based salted password hashing and secure comparison; no credential logging. |
| `evidencelogger.infrastructure.logging` | Package marker only | Safe rotating diagnostic logs. These are separate from permanent append-only domain audit history. |
| `evidencelogger.infrastructure.time` | `IdGenerator` contract | Injectable clock and ID generation for production and deterministic tests; it does not decide domain outcomes. |

## Key architectural rules

- Dependencies point inward: UI → services → domain/repository contracts → JDBC/infrastructure → SQLite.
- Services enforce role and current assignment on direct calls; role-specific UI visibility is only presentation.
- Controllers pass typed command data. The service obtains actor, role, current state, and time from its session/database/infrastructure dependencies.
- Each successful custody change and its audit event(s) commit atomically. History and corrections are append-only.
- Approval does not check evidence out; handoff plus Investigator acknowledgment does. Custodian inspection is required before a return becomes available in storage.
- Attachments, backup/restore, case closure, release from hold, account administration, and other deferred features have no package or database component in the MVP.

## Current implementation status

Implemented today: JavaFX launcher/lifecycle, placeholder shell, typed domain identifiers/enums, checkout command/query contracts, DTO validation/read models, session/authorization contracts, typed service exceptions, audit draft/writer contracts, transaction runner contract, and ID-generator contract.

Not implemented today: concrete authentication, casework and checkout services, repository interfaces/implementations, SQLite connection/migrations/schema resources, password hashing, logging configuration, role/login controllers, and the actual Custodian/Investigator screens. Consequently, the running application currently shows the placeholder shell rather than the complete workflow described by the specifications.
