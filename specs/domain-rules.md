# EvidenceLogger domain rules

**Status:** Domain decisions approved by the user on 20 Sep 2026 and updated on 26 Sep 2026. This document applies the approved product decisions D-01 through D-08 and implementation stack in `specs/product.md`. **Confirmed** means decided here or in the product specification; **Open** means no behavior has been chosen. Case closure and all backup and restore features remain outside the MVP.

## 1. Scope and glossary

**Confirmed:** The MVP records fictional physical evidence in an offline, single-workstation Java desktop application. The two roles are Evidence Custodian and Investigator. The core flow is registration, checkout request, decision, handoff, collection acknowledgment, examination notes, return initiation, Custodian inspection, and storage. Case closure, disposal, and account administration are outside the MVP.

**Confirmed by the user:** The Custodian retains physical custody after recording handoff until the Investigator acknowledges receipt; an evidence item may have at most one pending or approved checkout request at a time; a fresh request is permitted whenever the evidence is `IN_STORAGE` and has no pending or approved request; case closure and backup and restore remain outside the MVP. The user also approved the recommendations recorded in section 8, including the audited void workflow for erroneous evidence registration. Product decisions D-01 through D-08 are confirmed in `specs/product.md` and reflected here where they affect domain behavior.

**Confirmed setup rule:** Creating an empty local database seeds one fictional Custodian account and at least two fictional Investigator accounts. Their passwords are stored only as salted hashes, their credentials are documented as demo-only in the User Guide, and account administration remains outside the MVP.

| Term | Meaning and status |
| --- | --- |
| Case | The unit to which evidence and Investigator access are attached. **Confirmed.** |
| Assignment | A link granting an Investigator access to a case. A case may have multiple assigned Investigators; the Custodian may change assignments with an audit event. **Confirmed.** |
| Evidence item | One physical item registered to a case, with a generated unique reference, short description, and selected storage location. **Confirmed.** |
| Voided evidence | An erroneous registration retained with its reference and append-only history in terminal state `VOIDED`. It is not deleted or disposed. **Confirmed.** |
| Storage location | A Custodian-maintained choice for registration. The Custodian may add one; a location already used by evidence cannot be renamed or deleted. **Confirmed.** |
| Checkout request | An Investigator's request to take one item for a stated purpose until an expected return time. Approval is permission, not transfer of custody. **Confirmed.** |
| Handoff | The Custodian's record of offering an approved item for collection. The Custodian retains physical custody until the Investigator acknowledges receipt; only then is the item checked out. **Confirmed.** |
| Checkout | The period of collection associated with an acknowledged handoff. Examination notes belong to this specific checkout. **Confirmed.** The storage schema may represent this as a separate record. |
| Return initiation | The collecting Investigator's indication that a checkout is being returned. Notes freeze then. The Custodian records physical receipt at inspection. **Confirmed.** |
| Amendment/correction | A new, reasoned record linked to an earlier entry or note. The original remains unchanged. Custodians correct documentary custody history; note authors correct their own notes. **Confirmed.** |
| Audit event/history entry | An append-only record of a domain action with stable ID, actor, time, type, and subject links. **Confirmed.** |

### Domain entities

| Entity | Minimum relationship or data supported by the product | Status |
| --- | --- | --- |
| User | Identity, role, sign-in credential; actor for actions. An empty database is seeded with one fictional Custodian and at least two fictional Investigators using salted password hashes. | **Confirmed**; account administration is outside the MVP. |
| Case | Generated identifier, nonblank title, and zero or more assigned Investigators after creation. Creation includes one initial assignment; others may be added later. | **Confirmed**. |
| CaseAssignment | Case–Investigator association used by authorization; changes are audited. | **Confirmed** domain relationship. |
| EvidenceItem | Exactly one case, unique generated reference, nonblank short description, chosen location, and custody state. Case, description, and location are the only registration inputs; the reference and state are system-managed. | **Confirmed**. |
| StorageLocation | Unique nonblank name maintained by the Custodian; used locations remain unchanged. | **Confirmed**. |
| CheckoutRequest | One evidence item, requesting Investigator, purpose, expected return time, decision/status. | **Confirmed**. |
| Handoff/Checkout | Custodian who records handoff, requesting Investigator who acknowledges, their times, item and approved request; notes and return attach to the checkout. | **Confirmed** domain data; storage schema remains an implementation choice. |
| ExaminationNote | Nonblank text associated with one checkout; visible to the Custodian and Investigators currently assigned to its case; author may append a reasoned correction. | **Confirmed**. |
| AuditEvent | Stable immutable ID, action type, actor, time, subject links, and any required reason/comment or correction target. | **Confirmed**. |

## 2. Permissions and assignment

`C` = confirmed by the product or the user's decisions; `—` = unavailable to that role; `?` = still unresolved. Every permitted action is subject to its state preconditions and service-layer authorization. Signing in alone does not grant access to an unassigned case.

| Action | Evidence Custodian | Investigator |
| --- | --- | --- |
| Sign in; use separate role view | C | C |
| Create case and make initial assignment | C | — |
| Add/remove an existing assignment | C | — |
| View/search cases and evidence | C for work under Custodian control | C for assigned cases only |
| Register evidence; choose location | C | — |
| Void an erroneous evidence registration | C, with reason and only before any request workflow | — |
| Add a storage location | C | — |
| Relocate evidence | — | — |
| Submit checkout request; view its status | — for submitting; C for viewing | C for assigned case |
| Withdraw a pending request | — | C, own request only |
| Cancel an uncollected approval or reverse an unacknowledged handoff | C, with reason | — |
| Approve or reject a request | C | — |
| Record physical handoff | C | — |
| Acknowledge collection | — | C, collecting Investigator |
| Add note to checkout; initiate return | — | C, collecting Investigator |
| Inspect return; store | C | — |
| Record and inspect an unplanned return | C, with reason | — |
| Read custody history | C | C for assigned cases |
| Edit/delete an old history entry | — | — |
| Append a documentary custody-history correction | C | — |
| Append a correction to own examination note | — | C for assigned case |
| Close a case; dispose of evidence | — | — |

**Confirmed assignment restriction:** Every Investigator read, search, and write involving a case or its evidence requires a current assignment, including direct service calls. Request approval does not grant the Investigator access to an otherwise unassigned case. An Investigator cannot approve their own or anyone else's request.

**Confirmed assignment rule:** A case may have multiple assigned Investigators. The Custodian may add or remove assignments, and each change is audited. Removal fails while that Investigator has a `PENDING` or `APPROVED` request or a checkout not yet inspected for that case. An unacknowledged handoff is still an approved request. After removal, the former assignee cannot read that case's records, including its earlier notes and history. The request's collector must be its requester and must still be assigned at acknowledgment.

## 3. Independent state models

The request state represents permission; the evidence-custody state represents availability and custody workflow. A decision never itself changes the item to checked out. These state meanings are **Confirmed**; code-level names may differ.

### Checkout-request state

```text
PENDING ──approve──> APPROVED ──collection acknowledged──> CONSUMED
   │                    │
   ├──reject──> REJECTED └──Custodian cancel/reverse──> CANCELLED
   └──requester withdraw──> WITHDRAWN
```

- **Confirmed:** A new request is pending; the Custodian may approve or reject it. The requester may withdraw it only while pending. A Custodian may cancel an approved request before acknowledgment, with a reason. There is no automatic expiry.
- **Confirmed:** `CONSUMED` means an approved request produced one acknowledged checkout. It remains linked after return; return does not reopen it. `REJECTED`, `WITHDRAWN`, `CANCELLED`, and `CONSUMED` are terminal. Rejected and cancelled requests cannot be collected.
- **Confirmed:** A fresh request may follow any terminal request whenever the evidence is `IN_STORAGE` and no `PENDING` or `APPROVED` request exists for it. A consumed request therefore permits a fresh request only after the checkout has been inspected and the item has returned to `IN_STORAGE`.

### Evidence-custody state

```text
IN_STORAGE ──handoff recorded──> HANDOFF_AWAITING_ACK ──acknowledged──> CHECKED_OUT
     ▲                                                                      │
     │                                                              return initiated
     │                                                                      |
     └──────────────return confirmed───────────── HANDIN_AWAITING_ACK <─────┤

IN_STORAGE ──void erroneous registration (no request ever)──> VOIDED
```

- **Confirmed:** Approval leaves evidence in storage. Only handoff plus Investigator acknowledgment makes it checked out. A checked-out item cannot be checked out again. Every return requires Custodian inspection before the item is available in storage.
- **Confirmed:** The Custodian retains physical custody during `HANDOFF_AWAITING_ACK`; it is unavailable for another handoff. The Custodian may reverse an unacknowledged handoff with a reason, returning the item to `IN_STORAGE` and cancelling the request. The handoff record remains in history.
- **Confirmed:** Return initiation stops new examination notes and leaves the item `HANDIN_AWAITING_ACK` until the Custodian physically receives and inspects it and confirms the return to become `IN_STORAGE`. Corrections to earlier notes may still be appended.
- **Confirmed:** `VOIDED` is terminal and represents an erroneous registration, not disposal. Only a Custodian may void an `IN_STORAGE` item that has never had a checkout request, and a nonblank reason is required. The record and history remain readable.
- **Open:** Handling damage, loss, or another integrity concern discovered before the item can be physically returned is deferred to an incident workflow.

The location field names the assigned storage location; it is not proof of physical possession during handoff, checkout, or return. Actual-location tracking is outside this MVP.

## 4. Transition table

**Confirmed audit policy:** Each successful row writes its listed append-only audit event(s) in the same transaction as its record or state change. The event labels are descriptive names, not a required code naming convention. `R` is request state; `E` is evidence-custody state. A failed precondition produces no transition event.

| Action | Actor | Preconditions | Resulting state/record | Required audit event |
| --- | --- | --- | --- | --- |
| Create case and assign Investigator | Custodian | Nonblank case title; one valid Investigator. | Case and initial assignment created. | `CASE_CREATED`, `CASE_ASSIGNED` |
| Add case assignment | Custodian | Case and Investigator exist; assignment does not already exist. | Investigator gains case access. | `CASE_ASSIGNED` |
| Remove case assignment | Custodian | Assignment exists; that Investigator has no pending/approved request or uninspected checkout for the case. | Investigator loses case access, including past notes/history. | `CASE_UNASSIGNED` |
| Add storage location | Custodian | Nonblank name not already in the location list. | Location becomes selectable. | `LOCATION_ADDED` |
| Register evidence | Custodian | Case exists; description supplied; selected location exists. | New item `E=IN_STORAGE`; generated unique reference. | `EVIDENCE_REGISTERED` |
| Void erroneous evidence registration | Custodian | Item exists; `E=IN_STORAGE`; no checkout request has ever existed for the item; nonblank reason. | `E=VOIDED`; record retained and excluded from normal evidence searches. | `EVIDENCE_VOIDED` |
| Submit request | Assigned Investigator | Item belongs to assigned case; `E=IN_STORAGE`; nonblank purpose; expected return later than submission; no other `PENDING` or `APPROVED` request for the item. A prior terminal request does not block submission. | New `R=PENDING`; `E` unchanged. | `REQUEST_SUBMITTED` |
| Withdraw pending request | Requesting Investigator | `R=PENDING`; requester still assigned. | `R=WITHDRAWN`; `E` unchanged. | `REQUEST_WITHDRAWN` |
| Approve request | Custodian | `R=PENDING`; `E=IN_STORAGE`; requester still eligible; no competing active request. | `R=APPROVED`; `E=IN_STORAGE`. | `REQUEST_APPROVED` |
| Reject request | Custodian | `R=PENDING`. | `R=REJECTED`; `E` unchanged. | `REQUEST_REJECTED` |
| Cancel uncollected approval | Custodian | `R=APPROVED`; `E=IN_STORAGE`; nonblank reason. | `R=CANCELLED`; `E` unchanged. | `REQUEST_CANCELLED` |
| Record handoff | Custodian | `R=APPROVED`; `E=IN_STORAGE`; collector is requester and still assigned; no other active handoff/checkout. | `R=APPROVED`; `E=HANDOFF_AWAITING_ACK`; Custodian retains physical custody; record actor/time. | `HANDOFF_RECORDED` |
| Reverse unacknowledged handoff | Custodian | Matching handoff exists; `R=APPROVED`; `E=HANDOFF_AWAITING_ACK`; nonblank reason. | `R=CANCELLED`; `E=IN_STORAGE`; original handoff remains in history. | `HANDOFF_REVERSED` |
| Acknowledge collection | Requesting Investigator | Still assigned; matching recorded handoff; `R=APPROVED`; `E=HANDOFF_AWAITING_ACK`. | `R=CONSUMED`; `E=CHECKED_OUT`; checkout created/activated; record receiving actor/time. | `COLLECTION_ACKNOWLEDGED` |
| Add examination note | Collecting Investigator | Active checkout for an assigned case; return not yet initiated; nonblank text. | New note linked to checkout; `R` and `E` unchanged. | `EXAMINATION_NOTE_ADDED` |
| Initiate return | Collecting Investigator | Active checkout; `E=CHECKED_OUT`; no prior return initiation. | Mark checkout return initiated and freeze notes; `R=CONSUMED`, `E=HANDIN_AWAITING_ACK`. | `RETURN_INITIATED` |
| Inspect clean return and store | Custodian | Return initiated; physical item received; `E=HANDIN_AWAITING_ACK`; clean inspection. | Checkout completed; `E=IN_STORAGE`; `R=CONSUMED`. | `RETURN_INSPECTED_STORED` |
| Record and inspect unplanned return | Custodian | No return initiation; physical item received; `E=CHECKED_OUT`; nonblank reason. | Notes freeze; checkout completed; `E=IN_STORAGE`; `R=CONSUMED`. | `UNPLANNED_RETURN_INSPECTED` |
| Correct documentary custody history | Custodian | Target event exists; nonblank reason and correction text. | Original event unchanged; linked correction appended; `R`/`E` unchanged. | `HISTORY_CORRECTED` |
| Correct examination note | Original note author | Still assigned to case; target note exists; nonblank correction text and reason. | Original note unchanged; appended correction linked to it; `R`/`E` unchanged. | `EXAMINATION_NOTE_CORRECTED` |

An approved request that is never collected remains approved until the Custodian cancels it with a reason. An unacknowledged handoff remains visible and blocks another handoff until the Custodian reverses it with a reason. Neither changes automatically with time.

## 5. Duplicate actions and competing requests

- **Confirmed:** An item already checked out cannot be checked out again. A rejected request cannot be collected. Previous history entries cannot be edited or deleted.
- **Confirmed:** A repeated evidence void, approval, rejection, handoff, acknowledgment, return initiation, or inspection against an already-advanced record fails as an invalid transition and creates no duplicate event. A retry may read the current result, but it must not record a second physical action. A new checkout requires a new approved request and handoff after the item returns to storage.
- **Confirmed:** At most one `PENDING` or `APPROVED` request may exist per evidence item, including when the same Investigator submits again. A competing request must not be accepted. The item cannot be collected through another request while a handoff or checkout is active.
- **Confirmed:** `HANDOFF_AWAITING_ACK` and `CHECKED_OUT` block new requests. The single-active-request rule is enforced atomically; the losing action receives a clear conflict result. Approved requests may be cancelled before acknowledgment with a reason, and an unacknowledged handoff may be reversed with a reason.
- **Confirmed:** A new request may follow a terminal request as soon as the item is `IN_STORAGE` and has no `PENDING` or `APPROVED` request. Terminal request records remain unchanged in history.

## 6. Closed cases, compromised evidence, and amendments

### Closed cases

**Confirmed:** Case closure and disposal are deferred. The user also confirmed that closure stays outside the MVP. The MVP has no close/reopen transition and should not imply a closed-case status from inactivity or a completed checkout.

**Confirmed future rule, outside the MVP:** If closure is later added, all active requests and checkouts must be resolved before a case closes. A closed case blocks new evidence registration, requests, and handoffs, while preserving readable history and reasoned corrections. Reopening and other closed-case actions require a later specification.

### Amendments and corrections

**Confirmed:** History is append-only. A correction adds a new entry with actor, time, and reason; the old entry remains. Examination-note corrections are appended to the checkout's notes. Investigators cannot edit history.

**Confirmed:** Request rejection is part of the same ordered case/evidence history as registration, approval, handoff, return, and correction events. It is not confined to a separate request-status view.

**Confirmed:** The Custodian may append a documentary custody-history correction; the original author may append a correction to their own examination note while still assigned to the case. Both require nonblank correction text and reason, link to the original, and appear with it in ordered history. A correction cannot change current custody state, request decision, original actor, or original timestamp. State repair requires a separately approved compensating transition, which is outside this MVP. There is no general case or evidence metadata edit workflow; assignment changes have their own audited action.

## 7. Transactional and audit invariants

1. **Confirmed:** Every custody state change and its history entry commit or roll back together. Registration, decisions, handoffs, returns, and corrections produce append-only history. A failed operation leaves no partial state change.
2. **Confirmed:** A request decision does not move custody. Only a matching handoff and acknowledgment produce `CHECKED_OUT`; a checked-out item returns to `IN_STORAGE` only after Custodian inspection. A reversed, unacknowledged handoff returns to `IN_STORAGE` without a checkout.
3. **Confirmed:** Evidence references are unique. Every evidence item belongs to a case; every note belongs to one checkout; every checkout traces to its evidence and approved request. Used storage locations remain stable.
4. **Confirmed:** Service-layer checks enforce role and current case assignment on every operation, including direct calls. The active signed-in actor and time are recorded for custody-changing actions; a caller cannot supply another actor in their place.
5. **Confirmed:** Each item has at most one pending/approved request, one unacknowledged handoff, and one active checkout. A state transition checks its preconditions atomically so competing or repeated operations cannot both succeed.
6. **Confirmed cross-model consistency:** `HANDOFF_AWAITING_ACK` has exactly one matching approved request and unacknowledged handoff; `CHECKED_OUT` has exactly one active checkout from a consumed request and no initiated return; `HANDIN_AWAITING_ACK` has exactly one active checkout with an initiated return; `IN_STORAGE` has no active checkout; `VOIDED` has no checkout request of any status. These links update atomically with each transition.
7. **Confirmed:** Each transition and its audit event(s) commit or roll back together. Event subject and actor IDs remain valid; an event is written only for a successful transition. Handoff recording and later acknowledgment are separate transactions and events.
8. **Confirmed audit minimum:** Each event has an immutable, stable ID, type, signed-in actor ID and role, timestamp, and relevant case/evidence/request/checkout IDs. Include prior and resulting state for a state change, a reason/comment where required, and a target ID for a correction. Show history by timestamp with event ID as a tie-breaker.
9. **Confirmed:** Database constraints and conditional state changes protect uniqueness and workflow preconditions. A conflict or storage failure is shown clearly and leaves the prior state intact.

## 8. Approved domain decisions

| ID | Approved rule |
| --- | --- |
| DR-01 | Multiple Investigators may be assigned; Custodian assignment changes are audited; removal is blocked during that Investigator's active request or checkout. |
| DR-02 | The collector is the requester; the Custodian may reverse an unacknowledged handoff with a reason and audit event. |
| DR-03 | No automatic request expiry; requester may withdraw while pending; Custodian may cancel an uncollected approval with a reason. A fresh request is permitted whenever the evidence is `IN_STORAGE` and has no pending or approved request. |
| DR-04 | Custodian records physical receipt at inspection; new notes freeze when return is initiated; a reasoned Custodian inspection handles an unplanned return without initiation. |
| DR-06 | Custodian corrects documentary custody history; note author corrects their own notes; operational state repair needs a separate approved transition. |
| DR-07 | Evidence registration uses only case, short description, and selected location; the system generates the stable unique reference. Preserve used locations and give audit events stable IDs. |
| DR-08 | Closure is outside the MVP; if later added, resolve active requests/checkouts first, block new operations, and preserve history and corrections. |
| DR-09 | A Custodian may void an erroneous `IN_STORAGE` registration only before any checkout request exists, with a reason and append-only audit event; the retained `VOIDED` item is hidden from normal searches and is not disposal. |

These domain decisions, product decisions D-01 through D-08, and the implementation stack are approved. Behavior explicitly marked **Open** remains unresolved unless this document records an approved decision.
