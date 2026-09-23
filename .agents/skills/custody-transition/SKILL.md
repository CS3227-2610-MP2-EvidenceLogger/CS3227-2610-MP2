---
name: custody-transition
description: Implement or modify one EvidenceLogger checkout or evidence-custody transition through the approved service boundary, including authorization, atomic audit persistence, and transition-focused tests. Use for custody workflow behavior; do not use for UI-only work, read-only queries, unrelated casework, or unapproved new business rules.
---

# Custody Transition

Implement the requested transition as a small, reviewable change that preserves the approved state machines, role boundaries, transaction ownership, and append-only audit history.

## Trigger conditions

Use this skill when a task adds, changes, fixes, or reviews behavior that can advance or reject an EvidenceLogger checkout-request state, evidence-custody state, handoff, collection acknowledgment, return, or return inspection.

Do not use it for:

- UI-only presentation changes that do not alter transition behavior.
- Read-only queries or history display changes.
- Case, assignment, storage-location, authentication, packaging, or diagnostic-logging work unless it is necessary to the requested transition.
- Deferred features or transitions absent from the approved specifications.
- Specification design where the user has not asked for implementation.

## Required inputs and references

Before editing, identify the requested transition, its acceptance criteria, and the code area in scope. If any of these are unclear, inspect the task context and repository first; ask only when the ambiguity would change application behavior.

Read these sources before implementation:

1. `AGENTS.md` for repository workflow, scope, safety, testing, and reporting rules.
2. The applicable transition row and related authorization, state, duplicate-action, and testing sections in `specs/domain-rules.md`. Treat only confirmed rules as implementable behavior.
3. The service, authorization, transaction, repository, and testing sections in `specs/architecture.md`.
4. The current Gradle configuration and wrapper files to determine the commands and tasks that actually exist.
5. The relevant current production and test code, including the checkout command contract, authorization helpers, transaction runner, audit writer, domain states, repositories, migrations, and neighboring transition implementations.
6. `references/work-split-commit-plan.md` for ownership and shared-interface boundaries. Do not modify shared architecture used by another role without approval.
7. `references/l3.pdf`, `references/l4.pdf`, and `references/l5.pdf` for the repository-guidance, focused-skill, checkable-definition-of-done, guardrail, traceability, and workflow-verification principles used by this procedure. These lectures guide execution; they do not define EvidenceLogger business behavior.

The approved specifications and current code are authoritative over examples or assumptions. Do not infer a missing actor, state result, audit event, validation rule, or error behavior. Record unresolved behavior as an open question and continue only with independent work.

## Procedure

1. Establish the transition contract.
   - Locate the exact row in `specs/domain-rules.md`.
   - Record the authenticated actor, required role or assignment relationship, input validation, persisted preconditions, resulting request and custody states or records, and required audit event.
   - Separate confirmed requirements from proposed implementation decisions and open questions.

2. Trace the existing implementation before writing.
   - Follow the command from its `CheckoutCommands` input and `CheckoutCommandService` method through the current service implementation, domain logic, repository operations, transaction runner, and audit writer.
   - Find the closest implemented transition and its unit or SQLite integration tests.
   - Reuse established patterns. Do not duplicate shared logic or introduce an abstraction unless at least two consumers need it.

3. Define the smallest change.
   - Keep authoritative behavior in the service/domain and persistence layers, not in a JavaFX controller.
   - Use the existing `evidencelogger.service.checkout.CheckoutCommandService` boundary unless an approved specification explicitly changes it.
   - Avoid unrelated refactoring, dependency changes, edits to merged migrations, or changes owned by the other workstream.

4. Enforce actor and preconditions at the boundary.
   - Obtain the actor from the service-owned session and use the central authorization service; never accept an actor or role nominated by the UI.
   - Perform role, current-assignment or collector, input, and state checks required by the applicable specification.
   - Re-read checks whose truth may change inside the same write transaction as the transition.
   - Return the repository's existing typed service failures for unauthorized, invalid, conflicting, missing, or storage outcomes.

5. Apply the transition atomically.
   - Execute one command in one `TransactionRunner` transaction.
   - Pass the runner-owned connection through repository writes and `AuditEventWriter`; repositories and the writer must not commit, roll back, close, or start independent transactions.
   - Use the existing conditional-write and database-constraint approach for expected prior state and single-active-record invariants.
   - Update all specified state or records and append every audit event listed by the applicable transition row before the single commit.
   - Let any failure roll back both domain changes and audit inserts. Do not automatically retry custody-changing commands or create a domain audit event for a failed transition.

6. Add or update behavior tests.
   - Add the narrowest useful unit tests for domain rules, validation, and direct service authorization.
   - Add SQLite integration coverage when persistence, constraints, transactionality, or audit history is involved, using the real SQLite driver and isolated temporary databases as specified in `specs/architecture.md`.
   - Cover the valid transition; invalid actor, input, assignment, or prior state as applicable; repeated or competing execution without duplicate state changes or events; and an injected failure between the state write and audit append proving complete rollback.
   - Assert both the resulting domain state and the exact audit effect. For failures, assert that neither changed.

7. Verify after each logical change.
   - Run the focused affected test class or package first using tasks supported by the current Gradle build.
   - Before declaring completion, run the repository-required wrapper command from `AGENTS.md`: `./gradlew clean check` (using the checked-in platform wrapper as needed).
   - If a required command cannot run, capture the command, failure, and unverified requirements. Never report an unexecuted, failed, or skipped check as passing.

8. Review the finished diff.
   - Confirm the diff is limited to the requested transition and its tests.
   - Check that no UI path bypasses service authorization, no repository owns a transaction, no prior audit/history record is mutated, no sensitive free text is added to diagnostic logs, and no unresolved business rule was invented.

## Expected final report

Report:

- The transition implemented or modified and the confirmed specification row used.
- Files changed and the service/domain/persistence behavior affected.
- Actor and authorization checks, transition preconditions, resulting state, and audit event, by reference to the specification rather than by creating a new rule.
- Focused and repository-wide verification commands actually run, with pass, fail, and skipped results.
- Any failure, unverified requirement, open question, or manual check still required.
- Any approved deviation from the documented architecture or work split; otherwise state that none was introduced.

## Definition of done

- [ ] The relevant approved specifications, current build configuration, and existing transition code were read before the first edit.
- [ ] The actor, authorization rule, input and state preconditions, result, and audit event match one confirmed transition row.
- [ ] No unresolved business rule or deferred feature was implemented.
- [ ] The transition is reached through `CheckoutCommandService`, with authoritative checks outside the UI.
- [ ] Mutable authorization and state checks, state writes, and all required audit appends share one runner-owned transaction.
- [ ] A failure before commit leaves both custody/request data and audit history unchanged.
- [ ] Automated tests cover valid, invalid, duplicate or competing, and rollback behavior wherever each applies, including direct service authorization.
- [ ] Relevant focused tests pass after the logical change.
- [ ] `./gradlew clean check` completes successfully, or the final report states the exact failure and what remains unverified without claiming completion.
- [ ] The diff contains no unrelated refactor, new dependency, unapproved shared-architecture change, or edited merged migration.
- [ ] The final report is complete, accurate, and distinguishes verified results from skipped or unverified work.
