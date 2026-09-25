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
| Evidence Custodian | `custodian` | `CustodianDemo!2026` | A4 casework workspace |
| Investigator | `investigator.alex` | `InvestigatorDemo!2026` | Authentication only; workspace planned for A6 |
| Investigator | `investigator.blair` | `InvestigatorDemo!2026` | Authentication only; workspace planned for A6 |

Invalid credentials show a generic error. An Investigator can authenticate in the current build,
but the application immediately clears that session and returns to login because the separate
Investigator workspace has not yet been implemented.
