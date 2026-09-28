# EvidenceLogger User Guide

EvidenceLogger is an offline JavaFX desktop application for recording fictional evidence-custody
work. Java 25 is required.

<box type="warning">

**DISCLAIMER: #r#DO NOT## enter real evidence information; the application does not provide legal,
police, or forensic certification.**

</box>

---

## Quick Start

### 1. Start the application

Run the application from the repository root.

On Windows:

```powershell
.\gradlew.bat run
```

On macOS or Linux:

```bash
./gradlew run
```

On the first startup, EvidenceLogger creates a local data directory and prepares the empty database.
It applies the database migrations and creates the three demo accounts below.

No cases, assignments, storage locations, evidence, checkout requests, checkouts, or history
entries are created automatically. Add those records through the workspaces after signing in.

### 2. Sign in with demo data

These fictional accounts are seeded into a new local database. Do not reuse these passwords.

| Role | Username | Password |
| --- | --- | --- |
| Evidence Custodian | `custodian` | `CustodianDemo!2026` |
| Investigator | `investigator.alex` | `InvestigatorDemo!2026` |
| Investigator | `investigator.blair` | `InvestigatorDemo!2026` |

Enter a username and password, then select **Sign in**. Invalid credentials show an error and do
not open a workspace. To try Investigator access, first sign in as the Custodian and create a
case, add a storage location, and assign an Investigator.

---

## Features

This section is organized by the workspace and task being performed. Actions that are not valid
for the selected record are disabled by the application.

### 1. General workspace behavior

After signing in, EvidenceLogger opens a separate workspace for the account's role. Both role
workspaces (custodian and investigator) show the signed-in display name and a **Sign out** button
in the dark-blue header. Selecting **Sign out** exits the session and returns to the sign-in screen.

The application displays validation and operation messages at the bottom of the workspace. If a
storage failure is reported, preserve the displayed reference for the developer and do not edit
the local database manually.

### 2. Evidence Custodian workspace

The Custodian workspace has four tabs: **Cases**, **Evidence**, **Work queue**, and **Locations**.

#### 2.1 Create a case and manage assignments

1. Open **Cases** tab and select **New case**. This opens a small dialog window.
2. Enter a case title and choose the initial Investigator.
3. Select **Create case**.
4. Select a case in the list to load its details.
5. Under **Assigned investigators**, you may choose another Investigator and select **Assign investigator**.
6. To remove one, select the Investigator and choose **Remove selected investigator**. This opens
a dialog window. Press **OK** to remove the investigator from the case.

Use the case search field and **Search** to find cases by title. The selected case also shows its
registered evidence and **Case history** (in different tabs).

#### 2.2 Add storage locations

1. Open **Locations**.
2. Enter a location name.
3. Select **Add location**.

Locations become available during evidence registration. Use a distinct, meaningful name for
each storage location.

#### 2.3 Register evidence

1. Select a case under **Cases**.
2. Select **Register evidence** in the case's **Evidence** panel.
3. Enter a short description and choose a storage location.
4. Select **Review registration**.
5. Review the summary and confirm the registration.

EvidenceLogger generates the public evidence reference. If the selected case already contains an
item with the same description and location, the dialog displays a possible-duplicate warning.
This warning does not prevent registration when the items are genuinely separate.

#### 2.4 Search, filter, and void evidence

1. Open **Evidence**.
2. Enter a description, reference, case, or location and select **Search**.
3. Optionally filter by case, location, or custody status.
4. Select **Clear filters** to reset the search.

The table shows Description, Location, Case, and Status. Select an item to view its generated
reference, then select **Copy reference** to copy it to the clipboard.

To correct an erroneous registration, select the item and choose **Void erroneous registration**.
Enter a reason and confirm the final warning. Voiding is available only for evidence currently in
storage and with no checkout request history. The record, generated reference, reason, and history
remain retained; voiding cannot be undone and is not disposal. Voided items are hidden from normal
searches. Select **Include voided registrations** to find them.

#### 2.5 Process checkout work

Open **Work queue** and select **Refresh work queue** when you need the latest tasks. Choose one
of these categories:

- **Pending decisions**: requests awaiting approval or rejection.
- **Ready for handoff**: approved requests whose physical handoff has not been recorded.
- **Awaiting acknowledgement**: handoffs waiting for the Investigator to acknowledge collection.
- **Returns to inspect**: returns initiated by an Investigator and awaiting inspection.
- **Currently checked out**: active checkouts that have not entered return.
- **All active work**: all non-terminal work items.

Select a row to enable only the actions valid for that item. The queue displays the evidence
reference, case, Investigator, request purpose, status, and due or collected time.

##### Approve or reject a request

1. Select **Pending decisions**.
2. Select a request.
3. Choose **Approve** or **Reject** and confirm.

Approval gives permission but does not make the evidence checked out. Rejected requests cannot be
collected.

##### Cancel an approval

Select an approved request that has not entered handoff, choose **Cancel approval**, enter a reason,
and confirm. The reason is recorded in history.

##### Record or reverse a handoff

For an approved request under **Ready for handoff**, select **Record handoff** and confirm that the
evidence was offered for physical collection. If the handoff was recorded but the Investigator has
not acknowledged it, select **Reverse handoff**, enter a reason, and confirm.

<box type="info">

**Note:** The Investigator must acknowledge collection before the item becomes an active checkout.

</box>

##### Inspect a return

For a return under **Returns to inspect**, select **Inspect and store** and confirm that the item
was received, inspected, and stored. To record a physical return that was not initiated through the
normal workflow, select an active checkout under **Currently checked out**, choose **Record
unplanned return**, enter the reason, and confirm the inspection.

#### 2.6 Read and correct case history

Select a case under **Cases** and open **Case history**. Select **Refresh history** when needed.
To add a documentary correction, select an event, choose **Append correction**, and provide both
correction text and a reason. The correction becomes a new linked history entry; it does not replace
the original event or change custody/request state.

### 3. Investigator workspace

The Investigator workspace shows only cases currently assigned to the signed-in Investigator. It
has **My Requests** and **Active Checkout** sections, plus available evidence and custody history
for the selected case.

#### 3.1 Find assigned cases and evidence

1. Enter a case title, evidence reference, description, or location in **Search assigned cases and
   evidence**.
2. Select **Search**.
3. Select a case to load its evidence and custody history.

<box type="info">

**Note:** Unassigned cases are not visible to the Investigator.

</box>

#### 3.2 Submit or withdraw a checkout request

1. Select evidence in the **Evidence for Selected Case** list.
2. Enter a purpose.
3. Enter the expected return using `DD/MM/YYYY HH:MM`, for example `26/09/2026 17:00`.
4. Select **Submit Checkout Request**.

The request appears in **My Requests** with its status. The request must be for evidence
currently in storage and an item cannot have more than one pending or approved request.

To withdraw a request, select one marked `PENDING` in **My Requests**, then select **Withdraw
PENDING Request**.

<box type="info">

**Note:** Withdrawal is available only while the request is `PENDING`.

</box>

#### 3.3 Acknowledge collection and manage an active checkout

After the Custodian records the handoff, select the request awaiting collection and choose
**Acknowledge Collection**. This records the Investigator as the collector and changes the item to
an active checkout.

Select an active checkout under **Active Checkout** and choose **Initiate return** when the physical
item is ready to be returned. Notes are frozen while the return awaits Custodian inspection. The
Custodian must complete **Inspect and store** before the item returns to storage.

#### 3.4 Add or correct an examination note

1. Open **Active Checkout** and select an active checkout.
2. Enter the note in **Examination Notes**.
3. Select **Add examination note**.

To correct your own note, select it, enter the correction text and correction reason, then select
**Correct note**. Corrections are appended and the original note remains visible in the note
details.

---

## Additional information

### 1. First-startup data

An empty database is populated only with the three demo accounts. The passwords are stored as
salted password hashes. Casework and custody records are intentionally left empty so that a
Custodian can create a controlled demonstration flow.

### 2. Status and validation behavior

Required selections, purposes, reasons, and date formats are checked before an operation is sent.
Actions are disabled when the selected record is not in a valid state. Approval, physical handoff,
Investigator acknowledgement, return initiation, and Custodian inspection are separate steps.

---

## FAQ

### 1. Why can an Investigator not find a case?

Investigator visibility is assignment-scoped. Ask the Custodian to assign the Investigator to the
case, then search again.

### 2. Why is an approved request not checked out?

Approval grants permission only. The Custodian must record the physical handoff, and the
Investigator must acknowledge collection before the item becomes an active checkout.

### 3. Why can I not void an evidence registration?

Voiding is allowed only while the item is in storage and has never had a checkout request. Select
**Include voided registrations** to find records that were already voided.

### 4. Why are my notes unavailable after initiating a return?

Notes are frozen while the return awaits Custodian inspection. This preserves the record while the
physical item is being returned.

### 5. What should I do when an operation fails?

Read the status message at the bottom of the workspace, correct the selected record or entered
fields, and try again. If a storage failure is reported, preserve the displayed reference for the
developer and do not edit the local database manually.

## Workflow summary

| Role | Main actions |
| --- | --- |
| Evidence Custodian | Create cases, assign Investigators, add locations, register and search evidence, void eligible erroneous registrations, process checkout work, inspect returns, and correct case history |
| Investigator | View assigned cases and evidence, submit or withdraw requests, acknowledge collection, add or correct examination notes, initiate returns, and read assigned-case history |
