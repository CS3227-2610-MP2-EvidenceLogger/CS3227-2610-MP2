# EvidenceLogger User Guide

EvidenceLogger is an offline desktop application for fictional evidence-custody workflows. Java 25
is required. On Windows, run it from the repository root with:

```powershell
.\gradlew.bat run
```

## Demo-only accounts

Do not reuse these passwords or enter real evidence information.

| Role | Username | Password |
| --- | --- | --- |
| Evidence Custodian | `custodian` | `CustodianDemo!2026` |
| Investigator | `investigator.alex` | `InvestigatorDemo!2026` |
| Investigator | `investigator.blair` | `InvestigatorDemo!2026` |

Invalid credentials show a generic error. Successful sign-in opens the workspace for that role.
Use **Sign out** to clear the session and return to login.

## Evidence Custodian workflow

The Custodian workspace supports creating and searching cases, changing Investigator assignments,
adding storage locations, registering evidence, and searching evidence. Under **Checkout workflow**,
the Custodian can approve or reject pending requests, cancel approved requests, record or reverse a
handoff, and inspect planned or unplanned returns.

The **History** tab shows case events in timestamp and stable-event-ID order. Select an event to
append a documentary correction with both correction text and a reason. A correction creates a new
linked history entry; it does not replace the original or change custody/request state.

## Investigator workflow

Investigators can see only currently assigned cases. They can search assigned cases and evidence,
submit or withdraw checkout requests, acknowledge collection after handoff, add and correct their
own examination notes, initiate a return, and read history for assigned cases.

## MVP limitations

Attachments, backup and restore, case closure, disposal, reports/exports, account administration,
cloud synchronization, and legal or forensic certification are outside this MVP.
