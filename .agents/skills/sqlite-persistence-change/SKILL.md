---
name: sqlite-persistence-change
description: Implement or review EvidenceLogger SQLite migrations, connection setup, JDBC repositories, schema constraints, row mappings, conditional writes, or transaction behavior. Use for persistence changes that must preserve migration integrity and service error semantics; exclude UI, authentication policy, business-state design, release packaging, deferred features, and dependency changes unless explicitly requested.
---

# SQLite Persistence Change

Make the smallest persistence change that conforms to the current approved specifications, schema, callers, and transaction boundaries. Do not treat this skill as a source of schema or business rules.

## Scope

Use this skill for work involving:

- migration discovery, ordering, metadata, versions, or checksums;
- SQLite connection creation and per-connection pragmas;
- JDBC prepared statements, row mappings, and repository implementations;
- foreign-key, uniqueness, check, or partial-index constraints;
- conditional inserts or updates and affected-row checks;
- transaction commit, rollback, locking, or persistence-error translation.

Do not use it for UI-only work, authentication policy, designing business states or transitions, release packaging, deferred features, or dependency changes unless the user's task explicitly includes them. An explicit dependency change still requires the approval required by `AGENTS.md`.

## Authoritative context

Before the first edit:

1. Read `AGENTS.md`, the persistence and testing sections of `specs/architecture.md`, and only the applicable confirmed rules in `specs/domain-rules.md`. Do not implement open or deferred behavior.
2. Inspect the current Gradle build and wrapper, migration directory and migration runner, connection factory/setup, `TransactionRunner`, relevant repository interfaces and JDBC implementations, service callers, service error types and error translation, and focused persistence tests. If an expected component does not yet exist, record that fact; do not infer its design from this skill.
3. Trace the existing schema objects, migration metadata, constraints, mappings, call sites, and transaction ownership affected by the task. Check neighboring implementations before introducing a pattern or abstraction.

The approved specifications and current code are authoritative. Reference them in implementation notes and tests instead of copying schema or business rules into this skill. If an unresolved rule would change observable behavior, stop that part, report the decision needed, and continue independent work.

## Implementation rules

- Add a new, next-ordered migration for a schema change. Never edit an already-applied migration. Preserve the established filename/version convention, checksum verification, newer-version rejection, and atomic recording of each applied migration where SQLite permits it.
- Ensure every opened SQLite connection receives all required pragmas through the existing connection setup, including `foreign_keys=ON` and the configured bounded busy timeout. Do not rely on a pragma having affected another connection.
- Use prepared statements exclusively. Close statements and result sets with try-with-resources, map every selected column deliberately, and preserve the repository's existing null, enum, ID, and UTC-time conventions.
- Keep repository interfaces narrow and business-oriented. JDBC repositories may enforce persistence invariants with constraints and conditional SQL, but must not authorize actors or invent workflow policy.
- For conditional writes, include the expected persisted preconditions in SQL and verify the affected-row count. Preserve existing semantics for distinguishing not-found, conflict, invalid transition, constraint, lock, and general storage failures.
- Translate SQLite constraint and locking failures through the existing typed service-error path. Do not expose raw SQL, parameters, or driver messages to the UI, and do not automatically retry custody-changing writes.
- Preserve `TransactionRunner` ownership: it obtains and closes the connection, begins the transaction, commits once, rolls back on every failure, and restores connection state. Services coordinate one command in one transaction. Repositories and audit writers must not change auto-commit, begin, commit, roll back, close the runner-owned connection, or create nested transactions.
- Keep mutable precondition reads, conditional state writes, and required audit inserts on the same runner-owned connection when the applicable confirmed rule requires atomicity. Avoid unrelated refactoring and new abstractions without at least two consumers.

## Tests and verification

Add focused JUnit integration tests using the real Xerial SQLite driver and isolated database files under JUnit temporary directories; never use H2 or a shared developer database. Cover what the change can affect:

- migration from an empty database and, once prior versions exist, upgrade from every supported applicable prior version, including ordering, version, and checksum behavior;
- repository round-trip mapping, including null, enum, ID, and time behavior that applies;
- new or affected foreign-key, uniqueness, check, and partial-index constraints;
- conditional-write success and zero-row or competing-write failure semantics;
- transaction rollback after an injected failure, proving all related state and audit effects remain unchanged;
- readable translation of applicable constraint and locked-database failures through existing service errors.

Run the narrowest affected test class or package after each logical change. Then run the repository-mandated wrapper command from `AGENTS.md`, currently `./gradlew clean check` using the checked-in platform wrapper as needed. Report the exact commands and pass, fail, skipped, and unverified results honestly; never claim an unexecuted check passed.

## Expected final report

Report the persistence behavior changed, authoritative specification/code references used, files changed, migration compatibility and transaction/error semantics preserved, focused and repository-wide checks actually run, and any failures, skipped coverage, open questions, or manual checks. State whether dependencies, shared architecture, or applied migrations were changed; identify explicit approval if any was required.

## Definition of done

- [ ] Authoritative specs, current schema, affected callers, transaction/error boundaries, Gradle build, and relevant tests were inspected before editing.
- [ ] The change is scoped to the requested persistence behavior and introduces no unapproved business rule, deferred feature, dependency, or shared-architecture change.
- [ ] Schema changes use a new ordered migration; existing applied migrations and their checksums remain unchanged.
- [ ] Every connection receives required SQLite pragmas, and JDBC code uses prepared statements with deliberate mappings and resource cleanup.
- [ ] Repository code owns neither authorization nor transactions; the runner alone owns connection lifecycle, commit, and rollback.
- [ ] Constraints, conditional writes, locking, and failures preserve the existing typed service-error semantics.
- [ ] Applicable real-SQLite tests cover empty/prior-version migration, mapping, constraints, conditional writes, and rollback using isolated temporary databases.
- [ ] Focused checks and the mandated clean check passed, or the final report gives the exact failure or skipped coverage and does not claim completion.
- [ ] The final diff and report contain no unrelated work and accurately identify all verification performed.
