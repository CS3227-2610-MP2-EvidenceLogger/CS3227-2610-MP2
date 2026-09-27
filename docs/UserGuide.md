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

The Custodian workspace keeps the dark-blue application header and has four task areas:

- **Cases** lists cases on the left. Select a case to see its assigned Investigators, registered
  evidence, and history together. Use **New case**, **Assign investigator**, **Remove selected
  investigator**, or **Register evidence** without leaving the selected case.
- **Evidence** searches registered evidence in Description, Location, Case, and Status columns.
  Optional case, location, and status filters narrow the table. Select a row to see and copy its
  generated reference.
- **Work queue** groups active work into pending decisions, evidence ready for handoff, handoffs
  awaiting acknowledgement, returns awaiting inspection, and currently checked-out evidence. Only
  actions valid for the selected row are enabled. Actions that cancel or reverse work request their
  required reason in a dialog.
- **Locations** lists and adds the storage locations available during registration.

When registering evidence from a selected case, enter only its short description and storage
location. The application warns about a possible duplicate when that case already contains the same
description at the same location, then shows a final registration summary. The warning does not
block a legitimate separate item.

To correct a registration made in error, select the item under **Evidence** and choose **Void
erroneous registration**. Enter a reason and confirm the warning. Voiding is allowed only while the
item is in storage and has never had a checkout request. The record, generated reference, reason,
and history are retained; this is not deletion or disposal. Voided items are hidden normally and
can be found by selecting **Include voided registrations**.

Case history appears inside the selected case. Select an event to append a documentary correction.
A correction creates a new linked history entry; it does not replace the original or change
custody/request state.

## Investigator workflow

Investigators can see only currently assigned cases. They can search assigned cases and evidence,
submit or withdraw checkout requests, acknowledge collection after handoff, add and correct their
own examination notes, initiate a return, and read history for assigned cases.

## MVP limitations

Attachments, backup and restore, case closure, physical disposal, reports/exports, account
administration, cloud synchronization, and legal or forensic certification are outside this MVP.
