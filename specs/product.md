# EvidenceLogger — draft product specification

**Status:** Draft for team review; no product behavior in this document is approved yet.  
**Sources:** `references/requirements.md` (mandatory requirements) and `references/suggestion.md` (project proposal).  
**Terms:** **Mandatory requirement** comes from `references/requirements.md`. **Recommendation** means a proposed product choice. **Open decision** means alternatives remain to be settled before the affected behavior is implemented.

## 1. Purpose and intended users

**Recommendation.** EvidenceLogger is a desktop application for recording the whereabouts and handling of *fictional* physical evidence in a small investigation, compliance, or campus-security office. It helps staff register evidence, request access, record handoffs and returns, and inspect an append-only custody history. Its users are an **Evidence Custodian** and an **Investigator**. It is an internal prototype, not a system certified for real investigations or legal compliance.

## 2. Operating environment and mandatory requirements

**Recommendation.** Both roles use one controlled local workstation at different times. Application data resides locally; normal MVP workflows should work without a network connection. The proposed JavaFX interface and embedded database are implementation candidates, not required choices in `references/requirements.md`.

**Mandatory requirements to preserve:**

- Build a Java desktop application targeting Java SE 25. The team has two or three members, with one substantive user role per member; this two-member product has two roles. Use a simple, separate interface for each role and well-designed shared components. Each member completes one role's features and a meaningful share of team work. Do not reuse MP1.
- Use and evaluate basic **single-agent** software engineering customization during implementation and testing. Maintain a reflection document with at least three detailed skill examples and human-verified summaries of all AI prompts and interactions in `logs/`.
- Deliver production-minded engineering: automated testing, CI/CD, and monitoring or diagnostic capability, together with reliable behavior and code quality.
- Deliver source under `src/`, a formal GitHub production release with a build-generated JAR containing the needed JavaFX libraries and compatible across operating systems, `docs/UserGuide.md`, `docs/DeveloperGuide.md` with acknowledgements, `docs/Reflections.md`, a GitHub Pages product site, and `logs/` summaries. The guides must match the released product.
- Use a public repository named `CS3227-2610-MP2` in an organization named `CS3227-2610-MP2-[project-name]`; keep `master` current for the **29 September, 2 pm SGT** deadline. One member must submit the organization name and team GitHub usernames by **4 September, 2 pm SGT** through the channel specified in `references/requirements.md`. These are delivery obligations, not application features.

## 3. Role responsibilities

All responsibilities below are **recommendations from the proposal, narrowed for the MVP**. Authorization must be enforced in application services, not only by hiding controls.

| Role | MVP responsibilities | Later candidates |
| --- | --- | --- |
| Evidence Custodian | Create cases and assign investigators; register evidence and storage locations; review requests; approve or reject access; record handoff and inspect returns; inspect custody history; perform backup and restore. | Account administration, case closure, disposal, inventory reports, advanced storage management. |
| Investigator | View assigned cases and their evidence; search within that scope; request checkout; see request status; acknowledge collection; record examination notes; initiate returns; view permitted custody history. | Attach files, link derived evidence, request case closure, report detailed discrepancies. |

## 4. Prioritized MVP

**Recommendation.** Complete P0 before P1. Both priorities are part of the proposed MVP; defer stretch work until the full MVP and required deliverables are verified. Keep the interface to straightforward lists and forms.

| ID | Priority | Feature |
| --- | --- | --- |
| M-01 | P0 | Local sign-in and separate role views. |
| M-02 | P0 | Custodian case creation and linking an Investigator to a case. |
| M-03 | P0 | Evidence registration with a unique reference and storage location. |
| M-04 | P0 | Investigator case/evidence view, basic search, and checkout request. |
| M-05 | P0 | Custodian request decision and recorded collection/handoff. |
| M-06 | P0 | Examination notes, return initiation, and custodian inspection. |
| M-07 | P0 | Append-only custody history visible to authorized users. |
| M-08 | P1 | Custodian-triggered backup and tested restore for local records. |

**Separately listed stretch features:** attachment storage and integrity checks; detailed discrepancy/compromise handling; case closure/reopening and disposal; investigator-account administration; derived-evidence links; inventory and audit exports; overdue dashboards; automatic backups; QR labels and printable receipts; inactivity locking; record versioning; encryption. These are not MVP commitments.

## 5. Functional requirements

All FRs are **draft recommendations**, informed by the proposal. Their IDs remain stable even if wording changes. A decision reference means the exact behavior is still open in section 9.

| ID | Requirement | MVP | Decision |
| --- | --- | --- | --- |
| FR-01 | The application shall require local sign-in before showing case or evidence records and shall identify the active role. | M-01 | D-01 |
| FR-02 | The application shall provide separate Custodian and Investigator views and reject unauthorized actions in the service layer. An Investigator cannot approve requests, change storage locations, or alter custody history. | M-01 | — |
| FR-03 | A Custodian shall be able to create a case and assign an Investigator; an Investigator shall see only cases assigned to them. | M-02 | D-02 |
| FR-04 | A Custodian shall be able to register evidence against a case with a system-unique reference and a storage location. | M-03 | D-03 |
| FR-05 | An Investigator shall be able to list and search evidence within assigned cases and view its current availability and permitted custody history. | M-04 | D-02 |
| FR-06 | An Investigator shall be able to submit a checkout request stating a purpose and expected return time, then see its status. | M-04 | D-04 |
| FR-07 | A Custodian shall be able to view pending requests and approve or reject each request; only an approved request may proceed to collection. | M-05 | D-04 |
| FR-08 | Collection shall record the evidence, actor, time, and handoff outcome before the item is treated as checked out. An item already checked out cannot be checked out again. | M-05 | D-04 |
| FR-09 | The collecting Investigator shall be able to record examination notes for that checkout. | M-06 | D-05 |
| FR-10 | The Investigator shall be able to initiate return; the Custodian shall record an inspection and decide whether to place the item back in storage or retain it for review. | M-06 | D-06 |
| FR-11 | Registration, request decisions, handoffs, returns, and any correction shall create timestamped, attributable custody/audit entries that existing users cannot edit or delete. Authorized users shall be able to read the relevant history in order. | M-07 | D-07 |
| FR-12 | The Custodian shall be able to create a local backup and restore records from a valid backup through an explicit, guarded workflow. | M-08 | D-08 |

## 6. Measurable non-functional requirements

These are **recommended draft targets**, except where the source column says **Mandatory**. Record the test machine and data set when reporting results.

| ID | Target and verification measure | Source |
| --- | --- | --- |
| NFR-01 | On a documented test workstation, every MVP workflow shall complete with network access disabled after installation. No MVP action shall require a remote service. | Recommendation |
| NFR-02 | Every protected write shall be rejected for an unauthorized role in automated service-level tests; stored credentials shall contain no plaintext passwords. | Recommendation |
| NFR-03 | For each custody-changing operation, the record update and corresponding history entry shall commit together or both roll back. Integration tests shall inject at least one failure between these writes. | Recommendation |
| NFR-04 | With 100 cases and 1,000 evidence records on a documented test workstation, at least 95% of case/evidence list and search actions shall finish within 2 seconds over 20 measured runs. | Recommendation |
| NFR-05 | A backup/restore test shall recover all cases, evidence, active checkout state, and history from a known fixture, with matching record counts and references. A rejected or invalid backup shall leave the current data intact. | Recommendation |
| NFR-06 | Invalid input and database failures shall produce a user-readable error without an uncaught exception or partial custody change; diagnostic logs shall include time and failure context but no passwords. Test at least invalid credentials, invalid request data, and a storage failure. | Recommendation |
| NFR-07 | Automated unit and integration checks, plus a package build, shall run in CI for each proposed merge; a failed check shall be visible. Record release smoke-test results on the supported operating systems. | Mandatory: testing and CI/CD; recommended check detail |
| NFR-08 | The formal release shall include a build-generated JAR with required JavaFX libraries and launch successfully on Windows, macOS, and Linux. Record a smoke-test result for each OS; a failed or untested platform remains an unmet requirement. | Mandatory: cross-OS JAR; recommended verification |
| NFR-09 | Each role shall land in its own view after sign-in; a user shall be able to reach each of that role's MVP tasks through visible navigation without editing files or using a command line. | Mandatory: simple, separate role UI; recommended measure |

## 7. MVP acceptance criteria

These criteria test the **recommended MVP**, subject to the open decisions below. Use fictional fixture data.

| Feature | Acceptance criteria |
| --- | --- |
| M-01 | A valid Custodian and Investigator can each sign in and reach distinct role views. Invalid credentials cannot reveal records. Direct attempts to invoke the other role's protected action fail. |
| M-02 | A Custodian creates a case and assigns an Investigator; that Investigator sees it after sign-in, while another Investigator does not. |
| M-03 | A Custodian registers two evidence items in a case, each receives a different reference, and each shows its recorded storage location. An Investigator cannot register or relocate them. |
| M-04 | An assigned Investigator finds an item by basic search, submits a request with purpose and expected return time, and sees its pending status. An unassigned Investigator cannot access the case or request its item. |
| M-05 | A Custodian can approve one request and reject another. A rejected request cannot be collected. Collection of an approved request creates a handoff entry; a second checkout attempt for that item fails. |
| M-06 | The collecting Investigator records a note and starts a return. A Custodian inspects and records the outcome; the displayed location/availability reflects that outcome. |
| M-07 | Authorized users see registration, decisions, collection, and return in timestamp order with actors. Attempts to edit or delete a previous entry fail; a correction adds a new entry. |
| M-08 | A Custodian backs up a fixture dataset, changes the local records, restores the backup after confirmation, and sees the original records and history. An invalid backup is rejected without replacing the live data. |

## 8. Non-goals

**Recommendation.** No real case data, forensic analysis, police-database integration, legal or regulatory compliance claim, cloud service, multi-computer synchronization, hardware scanner, biometric matching, email/SMS notification, blockchain ledger, or external digital signatures. The MVP also excludes attachment handling, evidence disposal, and full case lifecycle management; any later work on these requires its own approved specification.

## 9. Unresolved decisions

Recommendations here are **not approvals**. Resolve each before implementing behavior that depends on it, and update the FRs and acceptance criteria as needed.

| ID | Question and alternatives | Recommendation and reason |
| --- | --- | --- |
| D-01 | How are initial accounts created: seeded fictional demo users, first-run setup, or Custodian-managed account creation? | Seed documented fictional demo users for the MVP, then consider account management later; smallest scope for a two-member demonstration. Decide credential handling before implementation. |
| D-02 | May Investigators see only assigned cases, or all cases with restricted actions? | Assigned cases only, matching the proposal's access boundary and simpler to test. Decide how assigned Investigators are changed. |
| D-03 | Which evidence fields and location model are mandatory: free-text location, predefined locations, or a managed location list? | Require case, short description, and a Custodian-selected location from a small managed list; generate references automatically. Decide identifier format and whether type/sensitivity fields are needed. |
| D-04 | Does approval itself check evidence out, or is there a separate handoff and Investigator acknowledgment? What happens when a request is cancelled or expires? | Separate approval from recorded handoff and acknowledgment for a clearer custody trail. Keep cancellation/expiry rules open until workflow review. |
| D-05 | Are examination notes attached to the request, the checkout, or the evidence record? Can they be amended? | Attach notes to a specific checkout and append corrections, preserving context. Confirm visibility and required fields. |
| D-06 | After return, can the item go directly to storage, or must it pass inspection? How are damage/discrepancies handled? | Require Custodian inspection; place clean returns in storage and hold problematic returns for review. Define inspection outcomes before coding. |
| D-07 | Which actions belong in immutable history, and how are incorrect entries corrected: amendment entry or privileged edit? | Append a correction entry with actor, time, and reason; never edit historical entries. Confirm the minimum event data and whether rejected requests belong in custody history or a separate audit view. |
| D-08 | Does backup contain database records only or also attachments? Is restore in-app or a documented offline operation? | Back up database records only while attachments are out of MVP; use a guarded in-app restore if feasible. Decide backup destination, replacement behavior, and recovery from interrupted restore. |
| D-09 | How will one release satisfy the cross-OS JAR and included-JavaFX requirement in `references/requirements.md`, given platform-specific JavaFX components? | Investigate packaging and smoke-test all three desktop OSs early; seek clarification if the required artifact interpretation remains unclear. |
