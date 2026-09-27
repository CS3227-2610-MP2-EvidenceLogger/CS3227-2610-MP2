---
name: iterative-tdd
description: Guide implementation through human-confirmed requirements, an approved checklist, graded red-green-refactor increments, reviewable commits, and an iteration-scoped visual diff. Use when a feature or bug fix should be implemented incrementally with behavioral tests and explicit human review between increments.
---

# Iterative TDD

Keep the human in control of requirements, design decisions, continuation, and
commit approval. Treat the grader as a release gate for every increment, not as
a substitute for tests or human review.

## Prepare requirements and plan

1. Identify the requested behavior and clarify ambiguities that affect
   acceptance criteria, architecture, schemas, or API contracts. Do not invent
   unresolved decisions.
2. Summarize the confirmed requirement and write an implementation checklist
   to `_temp/<short-slug>-plan.md` in the directory of invocation. Use one
   checkbox per stand-alone increment and include its behavioral tests and
   validation checks.
3. Ask the human to review the plan. Incorporate feedback and request approval
   again after any material revision. Do not edit application code before the
   human explicitly approves the plan.
4. Read [references/grading-evidence.md](references/grading-evidence.md) and
   prepare an evidence file for the first increment.

The `_temp/` directory is intentionally ignored by Git. Keep plans, evidence,
visual diffs, and snapshot metadata there; do not treat it as durable project
documentation.

## Run one implementation iteration

After plan approval, perform exactly one natural increment:

1. Select the next approved checklist item. For later increments, first record
   the human's explicit approval to continue from the preceding handoff.
2. Before editing application files, copy the complete current working tree to
   a temporary directory outside the repository, excluding only the real
   `.git/` directory. Initialize a temporary Git repository and commit this
   iteration baseline. Never substitute the real repository's `HEAD` for this
   snapshot.
3. Add or select the smallest behavioral test that proves the increment. Run
   it before production edits and confirm that it fails for the expected
   behavioral reason. If it passes, crashes for an unrelated reason, or cannot
   be run, stop and correct the test or evidence before implementing.
4. Make the minimum production change needed for that test to pass. Stop if a
   new human decision is required.
5. Run the focused test to green. Refactor only when useful, and rerun the
   focused test after refactoring.
6. Run applicable unit, integration, style, lint, and repository-required
   checks. Discover commands from repository instructions and configuration.
   Record actual commands and results; never report an unavailable, skipped,
   or failing check as successful.
7. Refresh the temporary comparison repository from the current working tree,
   again excluding only the real `.git/` directory. Use the
   `present-changes-visually` skill to compare the temporary baseline commit
   with the refreshed temporary worktree. Write the result to the project's
   ignored `_temp/visual-diff.html`. If that skill is unavailable, record the
   limitation and provide the best available iteration-scoped diff.
8. Complete the evidence manifest, apply
   [graders/iterative-tdd-grader.md](graders/iterative-tdd-grader.md), and run:

   ```bash
   python3 <skill-directory>/scripts/grade_iteration.py <evidence.json>
   ```

   Do not mark the checklist item complete unless output correctness and
   process compliance both receive `PASS`. Treat missing essential evidence as
   `UNVERIFIABLE`. Fix failures within the approved scope and grade again; ask
   the human when remediation requires a new decision.
9. Prepare a scoped Conventional Commit message. If the task explicitly
   authorizes commits and the grade passed, create the commit, record the
   outcome, and rerun the deterministic grader. Otherwise leave the working
   tree ready for the human to commit.
10. Mark the plan checkbox complete only after the final grade passes. Explain
    the rationale, advantages, disadvantages, test results, grade, and visual
    diff. State the next increment, then stop and wait for human approval. Do
    not begin another increment in the same uninterrupted turn.

Clean up the external snapshot after its visual diff and grade evidence are no
longer needed. Do not delete the current iteration's evidence before handoff.

## Finish the approved work

After the last increment, run every repository-required final check, grade the
aggregate evidence, and report failures or skipped checks accurately. Declare
completion only when every checklist item and both grading dimensions pass.

## Guardrails

- Test observable behavior, not implementation details or coverage alone.
- Preserve existing user changes and avoid destructive Git operations.
- Never stage, commit, reset, or modify the real Git index to create a visual
  diff.
- Never fabricate or reconstruct missing chronological evidence after the
  fact.
- Do not commit secrets or `_temp/` artifacts.
- Follow repository instructions even when they are stricter. Do not use an
  independent grading agent when a repository requires one agent; apply the
  same grader as a clearly separated second-pass audit instead.
