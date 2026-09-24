---
name: code-review
description: Review the EvidenceLogger Java codebase against the approved specifications, role authorization rules, error-handling expectations, tests, Java conventions, design quality, and documentation. Report findings in _temp/CodeReview.md; never fix findings unless the human explicitly approves them and asks for implementation.
---

# EvidenceLogger code review

Review this project as a reporting-only code review. Do not modify application code, tests, documentation, build files, or specifications while reviewing. Do not automatically fix, refactor, or “clean up” a finding. If the human later approves specific findings and requests fixes, stop reviewing and implement only the approved scope in a separate task. Do not trigger this skill unless the user asks explicitly to use this skill.

## Review sources and scope

Read the repository `AGENTS.md`, then read all applicable files under `specs/` and the existing source, tests, build configuration, and user/developer documentation before judging behavior. Treat the specifications as the source of truth. Distinguish evidence as:

- **Confirmed requirement**: explicitly stated in an approved specification.
- **Proposed decision**: an architecture or implementation proposal that is not itself a confirmed behavior rule.
- **Open question**: unresolved in the specifications; do not report a speculative violation as a defect. Record the ambiguity and the decision needed when it affects behavior.

Review the whole codebase, not only changed files. Include production code, tests, resources, migrations, configuration, and documentation. Use focused searches and the available build/test checks as evidence. Never treat a green build as proof that authorization, workflow, or test coverage is correct.

## Required checks

Group every finding under exactly one of these sections in the report:

1. **Correctness** — Compare actual behavior with product, domain, and architecture specifications. Check state transitions, custody/audit invariants, transactions, persistence constraints, authentication/session behavior, current assignment checks, and excluded MVP features. Identify the expected outcome, observed code path, and concrete evidence.
2. **Role-access violations** — Check `investigator` and `custodian` access at service boundaries and direct-call paths, not only UI visibility. Verify that Investigator reads and commands are limited to currently assigned cases, and that Custodian-only operations cannot be reached by an Investigator. Check that actor/session identity is not caller-controlled.
3. **SRP / DRY violations** — Check whether responsibilities cross UI, services, domain, repositories, infrastructure, and logging/audit boundaries. Identify duplicated authorization, transition, transaction, mapping, validation, hashing, time/ID, or error-translation logic. Report only duplication or coupling that creates a concrete maintenance or correctness risk; do not demand abstractions for deferred features.
4. **Error handling** — Check typed domain failures, validation, storage/SQLite failures, transaction rollback, conflict handling, user-readable messages, diagnostic logging, sensitive-data redaction, and JavaFX background-task behavior. Look for swallowed exceptions, overly broad catches, leaked SQL/stack details, partial writes, and incorrect retry behavior.
5. **Test gaps** — Check whether changed and critical behavior has automated tests, especially direct service authorization, assignment filtering, state transitions, append-only corrections, transaction rollback, constraints/conflicts, migrations, authentication, and error paths. Report meaningful untested risks, not a demand for tests for trivial getters.
6. **Java conventions** — Apply the SE Education Java coding standard at https://se-education.org/guides/conventions/java/index.html. Check package/name conventions, explicit imports, layout/line length/braces, modifier and member ordering, variable scope/initialization, encapsulation, boolean naming, test names, and required Javadocs/comments. Use the linked Google style guide only for topics the project standard does not cover.
7. **Unnecessary complexity** — Find speculative abstractions, duplicated layers, dead code, premature generalization, overcomplicated control flow, or unsupported features that conflict with the approved minimal architecture/MVP. Explain the simpler supported design and why the current complexity has a cost.
8. **Documentation mismatch** — Compare `docs/`, public Javadocs, comments, migration/resource descriptions, and user-facing behavior with the actual implementation and approved specifications. Report claims that are stale, missing, misleading, or inconsistent with role access, demo credentials, supported workflow, platform/build checks, or deferred features.

## Finding quality

Report actionable findings only. Each finding must include:

- a stable ID such as `COR-001` or `ROLE-001`;
- severity: `Blocker`, `High`, `Medium`, or `Low`;
- a concise title;
- exact file path and line number(s), or the relevant spec/test path;
- evidence and reasoning, including the expected behavior;
- impact/risk;
- a suggested direction, without implementing it;
- whether verification was static inspection, an executed check, or an unverified concern.

Do not count the same root cause in multiple sections; cross-reference the original ID if needed. Separate confirmed findings from open questions. A lack of findings is a valid result, but still record what was checked and which checks were skipped or unavailable. Do not invent business rules to make a finding.

## Verification

Run only relevant, non-destructive available checks after inspection, such as the Gradle wrapper tests/build or focused tests. Do not change code to make checks pass. Capture the actual command/check name and result, including failures and skipped checks, in the report. If the project is still a skeleton, say so and report the resulting coverage limitations rather than inferring defects from missing implementation.

## `_temp/CodeReview.md` output and review history

Create `_temp/` if needed and ensure `_temp/` and the files in it do not appear in the git revision history (you may edit `.git/info/exclude` to prevent tempering with the `.gitignore` file). Maintain exactly one `_temp/CodeReview.md`; do not create dated copies or alternate names.

The document must have this structure:

```markdown
# EvidenceLogger Code Review

## Current review — YYYY-MM-DD
### Review scope and verification
...
### Correctness
...
### Role-access violations
...
### SRP / DRY violations
...
### Error handling
...
### Test gaps
...
### Java conventions
...
### Unnecessary complexity
...
### Documentation mismatch
...

---

## Previous review — YYYY-MM-DD or No previous review
...
```

On every use, read the existing `_temp/CodeReview.md` before overwriting it. Put the new current review first. Preserve the complete immediately preceding review as the Previous review section; if an older previous section already exists, discard that older section so the file contains exactly two iterations. On the first use, include `No previous review` and state that no earlier report was available. Preserve the previous review verbatim except for moving it below the new current review and its heading if necessary. Ensure the report contains all eight required sections even when a section says `No findings identified`.

## Human approval boundary

The skill's default output is findings plus `_temp/CodeReview.md` only. End with a short note that no fixes were made. If the human approves findings and explicitly asks to fix them, treat that as a new implementation request: confirm the approved finding IDs, read the relevant specifications again, make only those fixes, update/add tests, run checks, and report any remaining findings. An approval to review or to create the report is not approval to modify code.
