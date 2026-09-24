---
name: code-refactor
description: Analyze and safely refactor explicitly scoped EvidenceLogger Java code for SOLID, DRY, and related design-principle violations while preserving behavior, APIs, stack compatibility, and project architecture. Use when the user explicitly requests this skill or asks for a scoped architectural refactoring.
---

# Code refactoring

Use this skill only for an explicitly requested refactoring of the named classes, methods, or package. It is an implementation skill: inspect first, make only the approved scoped changes, add or update behavior tests, and verify the result. Do not turn the task into a repository-wide cleanup.

## Required context

Before proposing or editing code, read `AGENTS.md`, the applicable files in `specs/`, the complete explicitly scoped production code, its tests, and nearby interfaces/callers. Treat specifications as the source of truth and distinguish:

- **Confirmed requirements** from approved specifications.
- **Proposed decisions** that guide architecture but do not change behavior.
- **Open questions** that could alter business behavior; stop and ask if they block a safe refactor.

Respect the existing EvidenceLogger boundaries: JavaFX UI/controllers, application services and authorization, pure domain, repository interfaces, JDBC repositories, transaction infrastructure, and diagnostic/audit logging. Do not introduce an abstraction unless at least two in-scope consumers need it.

## Review workflow

1. Establish the exact scope and current behavior from callers, tests, state mutations, exceptions, transactions, threading, and public signatures.
2. Identify concrete smells, not style preferences. Look for mixed responsibilities, duplicated authorization/validation/mapping/error handling, role-specific copies of shared logic, long methods, feature envy, inappropriate dependencies, and abstractions with no real second consumer.
3. Rank findings by behavioral risk and maintenance value. Record the affected symbol, evidence, principle, proposed change, and verification plan.
4. For each accepted change, state a concise rationale tied to a principle, for example: “Extracted JDBC mapping to the repository layer to satisfy SRP and keep services independent of persistence details.”
5. Implement the smallest coherent refactor inside the stated scope. Preserve business rules, execution order, side effects, transaction ownership, thread behavior, exception categories/messages where externally visible, persistence shape, role separation, and public API contracts.
6. Add or update focused tests for preserved behavior and new seams. Prefer existing hand-written fakes and the project’s JUnit/SQLite approach; do not add dependencies without approval.
7. Run `./gradlew clean check` before completion, plus focused checks during iteration when practical. Report every failed, skipped, or unavailable check honestly.

## Principles to apply

### SOLID

- **SRP:** Separate UI presentation, use-case coordination, authorization, domain decisions, persistence, transaction ownership, and diagnostics only when the split reflects a real responsibility.
- **OCP:** Prefer stable use-case/domain seams and narrow interfaces when a new variation is already required. Do not speculative-generalize deferred features.
- **LSP:** Preserve substitutability of repository/service fakes and implementations; do not create overrides that weaken preconditions, change failure semantics, or silently omit audit/transaction work.
- **ISP:** Keep interfaces narrow and business-oriented. Avoid a large role-neutral or generic CRUD interface that exposes operations a consumer must not use.
- **DIP:** Keep domain and services independent of JavaFX/JDBC where the architecture requires it; wire concrete implementations at application composition roots.

### DRY

Consolidate genuinely identical policy or transformation logic with multiple current consumers, especially authorization, transaction handling, ID/time generation, row mapping, and error translation. Do not deduplicate merely similar code when role policy, state transitions, or timing semantics differ. Preserve intentional defense-in-depth checks across service, domain, and database layers.

### Additional principles

- **KISS:** Choose the smallest design that removes the demonstrated problem; avoid factories, generic frameworks, or layers that add indirection without a current consumer.
- **YAGNI:** Do not build for unapproved attachments, backup, administration, future roles, or other deferred features during a refactoring pass.
- **Separation of concerns / information hiding:** Keep role UIs separate, hide persistence details behind the approved interfaces, and expose only the data and operations each caller needs.
- **Fail-fast and least privilege:** Preserve early validation and service-level authorization; never make UI visibility the security boundary or broaden access while extracting code.

When principles conflict, behavioral preservation and the approved specifications take precedence. Performance matters: avoid needless allocation, synchronization, database round trips, or abstraction layers, especially around the single desktop executor and SQLite transactions.

## Guardrails and output

Never add features, alter external contracts, change business rules, or widen scope during this skill. Do not edit unrelated files or refactor both user roles independently when a shared in-scope component can serve both. Do not modify `AGENTS.md`, skills, or specifications as part of ordinary refactoring.

For analysis-only requests, report findings without editing. For implementation requests, summarize:

- the exact scope changed;
- each change with its principle-based rationale;
- how behavior and public contracts were preserved;
- tests/checks run and their actual results;
- unresolved questions or risks.

If a proposed extraction would require an unresolved business rule, shared-architecture approval, a new dependency, or a materially broader scope, pause that change and ask for direction while completing independent safe work.
