# EvidenceLogger

EvidenceLogger is an offline JavaFX desktop application for managing fictional evidence-custody
workflows. Java 25 is required.

## Run locally

On Windows, start the application from the repository root:

```powershell
.\gradlew.bat run
```

## Demo-only accounts

These accounts and passwords are fictional demonstration data. Do not reuse these passwords for
real accounts or enter real evidence information into the application.

| Role | Username | Password | Current access |
| --- | --- | --- | --- |
| Evidence Custodian | `custodian` | `CustodianDemo!2026` | Casework, evidence, task queue, returns, and history |
| Investigator | `investigator.alex` | `InvestigatorDemo!2026` | Assigned casework, checkout, notes, returns, and history |
| Investigator | `investigator.blair` | `InvestigatorDemo!2026` | Assigned casework, checkout, notes, returns, and history |

Invalid credentials show a generic error. Successful sign-in opens a separate workspace for the
account's role, and Sign out clears the session before returning to login.
