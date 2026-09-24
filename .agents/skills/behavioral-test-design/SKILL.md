---
name: behavioral-test-design
description: Select and implement the smallest EvidenceLogger test boundary that proves changed behavior across domain, service, SQLite, presentation, acceptance, regression, or rollback risks. Use with feature and bug-fix commits for either role; do not use coverage percentage or implementation-detail assertions as substitutes for behavioral evidence.
---

# Behavioral Test Design

Prove the changed EvidenceLogger behavior at the lowest boundary that can observe the real risk. Add broader tests only when the behavior crosses components; do not repeat the same assertion at every layer.

## Establish the proof obligation

Before editing production or test code:

1. Read `AGENTS.md`, the applicable acceptance checks in `specs/product.md`, confirmed rules in `specs/domain-rules.md`, the testing and affected-layer sections of `specs/architecture.md`, and ownership boundaries in `references/work-split-commit-plan.md`.
2. Inspect the current Gradle test, check, and coverage configuration plus the relevant production path and neighboring tests. Treat current tasks and dependencies as evidence; do not assume a test suite, runner, threshold, or library exists.
3. State the observable behavior being changed: actor and authorization, input, starting state, action, result or failure, persisted effects, audit effects, and unchanged effects. Do not invent an unresolved rule.
4. Build a compact requirements-to-tests map before the first edit. For each applicable requirement, record the risk, smallest boundary, fixture/collaborators, assertions, and focused command. Mark non-applicable cases instead of creating ceremonial tests.
5. Check whether an existing test already proves the obligation. Extend the nearest behavioral test when that is clearer than adding a parallel fixture.

## Choose the smallest boundary

| Risk to prove | Default boundary | Assert |
| --- | --- | --- |
| Pure domain validation, value semantics, or state rule | JUnit Jupiter unit test without JavaFX or SQLite | Returned value/state or typed rejection at the public domain boundary |
| Role, current actor, assignment, collector, authorship, or public-service authorization | Direct call to the public service with injected hand-written fakes | Authorized result; wrong-role, unsigned, unassigned, or wrong-actor rejection; no forbidden effects |
| Migration, SQLite constraint, JDBC mapping, conditional write, lock/error translation, or real transaction behavior | Integration test using Xerial SQLite and a fresh `@TempDir` database | Rows and mappings visible through supported boundaries, exact constraint/conflict semantics, and unchanged losing state |
| Controller, presenter, navigation, validation display, async enable/disable, or readable-error adaptation | Presentation test around the meaningful adapter behavior with a fake service/session | Inputs sent to the service and observable view/navigation state or error mapping |
| Workflow spanning services, transactions, roles, or history | Service/database acceptance test at the narrowest composition that includes every participating component | User-visible sequence, final state, authorization isolation, and ordered audit/history effects |
| Escaped defect | Regression test at the lowest boundary that reproduces it | Failure before the fix and the public invariant after it, without pinning the implementation |
| State/audit partial-write risk | Service plus real-SQLite rollback integration test | Pre-state restored and no partial record or audit event after the injected failure |

Move down to real SQLite whenever SQL, a database constraint, transaction ownership, concurrency/competition, mapping, or persisted isolation is part of the claim. Move up to acceptance scope only when no smaller boundary can observe the interaction. A test using a production class may still be a unit test if all external boundaries are replaced by small fakes.

## Design the cases

Start with one success case, then select every failure dimension that can invalidate the changed behavior:

- **Actor:** unauthenticated, wrong role, stale assignment, wrong collector/requester/author, or caller-supplied identity ignored.
- **Input:** null, blank, malformed, missing required comment/reason, or invalid time/value when specified.
- **State:** illegal prior state, missing record, terminal record, or operation attempted after authority changes.
- **Isolation:** an Investigator cannot read or mutate another case; results are constrained before presentation, not filtered only in the UI.
- **Duplicate or competition:** a retry does not repeat a physical action or event; at most one competing write wins and the loser receives the documented failure.
- **Exact audit:** successful behavior records the required event type, signed-in actor and role, injected time and ID, subjects, prior/result states, and required reason/comment/correction. A rejected operation records no success event.
- **Rollback:** inject a failure at the relevant seam after the domain/state write and before the audit append or commit; assert all participating rows and events remain exactly at the pre-operation state.

Apply only relevant dimensions, but explain omissions when the task or acceptance criterion makes one likely. For transitions, assert both the domain result and audit effect. For failures, assert the exception/result and absence of every prohibited side effect.

## Implement stable tests

- Use JUnit Jupiter already configured by the repository.
- Inject `Clock` and ID sources so timestamps, ordering, and identifiers have exact assertions. Use small, local hand-written fakes for repository, service, session, or transaction collaborators; keep their behavior limited to the scenario.
- Use the real Xerial driver and isolated database files under JUnit `@TempDir` for SQLite integration. Run migrations through production migration code unless the test specifically targets a supported prior schema.
- For atomicity, prefer a repository or audit-writer decorator that throws at the desired point over a production test flag. Exercise the production `TransactionRunner` and query the database after the failed transaction.
- Test public contracts and durable outcomes. Do not assert private methods, internal call order without a contract, SQL spelling, JavaFX framework behavior, or incidental collection/class structure.
- Keep fixtures deterministic and visibly distinguish actors, cases, evidence, requests, and expected events. Share helpers only when at least two tests genuinely benefit and the helper does not hide the behavior under test.
- Do not use H2, a shared developer database, sleeps, uncontrolled wall-clock time, or random IDs for expected values.
- Do not add Mockito, TestFX, or any dependency without separate approval. Do not change production semantics, widen visibility, or add a production-only seam solely to make a weak test pass.
- Treat coverage reports as navigation for unexamined code, not evidence of correctness. Do not add tests whose only value is increasing a percentage or duplicating JUnit, JDBC, SQLite, or JavaFX framework guarantees.

## Focused workflow and verification

1. Make one logical behavior change with its smallest proving test. If fixing a defect, demonstrate that the regression test fails for the intended reason before the fix when practical and safe.
2. Run the narrowest supported Gradle test selector for the changed class or package after that logical change. Resolve product failures without weakening assertions; distinguish infrastructure failures from behavior failures.
3. Review the requirements-to-tests map against the finished diff. Confirm success and each applicable invalid actor, input, state, isolation, duplicate/competition, exact-audit, and rollback risk are proved once at the right boundary.
4. Run the repository-required clean check from `AGENTS.md` using the checked-in wrapper, currently `./gradlew clean check` or `./gradlew.bat clean check` on Windows. Run additional coverage or packaging tasks only when the task or current repository instructions require them.
5. Record automated passes, failures, and skips separately from manual-only checks. Never call an unexecuted check passed, and do not call the work complete while the required clean check is failing.

## Expected final report

Report:

- the changed behavior and authoritative requirement or acceptance check;
- the compact requirements-to-tests map, including any applicable case intentionally left manual or unproved;
- tests added or changed and why each selected boundary is the smallest one that proves its risk;
- production files changed, if any, and confirmation that no semantics were changed only for testing;
- focused and clean-check commands actually run, separated into passed, failed, skipped, and not-run results;
- manual-only checks, unresolved requirements, environmental blockers, dependency approvals, and remaining risk;
- whether real temporary SQLite, injected clocks/IDs, and hand-written fakes were used where applicable.

## Definition of done

- [ ] Approved requirements, acceptance checks, architecture testing guidance, work ownership, current Gradle configuration, and relevant code/tests were inspected before editing.
- [ ] A requirements-to-tests map identifies each applicable risk and the smallest boundary capable of proving it.
- [ ] Tests assert public behavior and durable effects rather than private implementation details or coverage percentage.
- [ ] Success and every applicable actor, input, state, isolation, duplicate/competition, audit, regression, and rollback case are covered or reported as unproved/manual with a reason.
- [ ] Unit/service tests use JUnit Jupiter, deterministic clocks/IDs, and focused hand-written fakes where appropriate.
- [ ] Persistence and atomicity claims use real SQLite temporary databases; no H2 or shared developer database is used.
- [ ] Rollback evidence observes both domain data and audit history after a deliberately injected mid-transaction failure.
- [ ] No unapproved test dependency, speculative abstraction, weakened production rule, or unrelated refactor was introduced.
- [ ] Focused tests pass after each logical change, and the repository clean check passes.
- [ ] The final report separates passed, failed, skipped, not-run, and manual-only verification and states remaining risk honestly.
