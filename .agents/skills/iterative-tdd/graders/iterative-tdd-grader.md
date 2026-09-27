# Iterative TDD Grader

Grade one implementation increment without changing files or repairing the
work. Inspect the confirmed requirements, approved plan, raw interaction and
command trace, evidence manifest, test output, and iteration-scoped diff. Do
not accept unsupported statements in the manifest as proof.

## Verdicts

Report `PASS`, `FAIL`, or `UNVERIFIABLE` independently for output correctness
and process compliance. Overall `PASS` requires both dimensions to pass. A
known violation is `FAIL`; absent evidence is `UNVERIFIABLE`. A critical known
violation takes precedence over unrelated missing evidence.

## Output correctness

Pass only when all of the following are supported by raw evidence:

1. Every confirmed acceptance criterion is implemented and mapped to at least
   one meaningful test, diff location, or manual observation.
2. Focused tests exercise observable behavior and pass after implementation.
3. Every repository-required check passes. Optional unavailable checks are
   identified accurately with a reason.
4. The diff stays within approved scope and preserves unrelated user work.
5. The handoff describes executed, failed, and skipped validation accurately.

Fail for an unmet criterion, a failing required check, irrelevant tests, an
out-of-scope behavior change, or a materially false completion claim.

## Process compliance

Pass only when evidence proves this order:

1. The human approved the checklist before application edits.
2. For a later increment, the human approved continuation after the previous
   handoff and before the new baseline or edits.
3. A complete external working-tree baseline was captured before edits.
4. The focused test failed for the intended behavior before production work.
5. Production work began only after red, then the focused test reached green.
6. Required validation and the iteration-scoped visual diff were completed.
7. The diff used the captured baseline, not the real repository's `HEAD`.
8. A real commit was created only when explicitly authorized and after a pass.
9. The checklist was marked complete only after both dimensions passed.
10. The agent handed off one increment and did not start the next without a
    subsequent human approval.

Critical failures include editing before plan approval, implementing before
red, using `HEAD` as the iteration baseline, fabricating evidence, creating an
unauthorized commit, or continuing without human approval.

## Required report

Return this structure and cite the evidence for every nontrivial finding:

```json
{
  "overall_verdict": "PASS | FAIL | UNVERIFIABLE",
  "output_correctness": {
    "verdict": "PASS | FAIL | UNVERIFIABLE",
    "findings": [],
    "evidence": []
  },
  "process_compliance": {
    "verdict": "PASS | FAIL | UNVERIFIABLE",
    "findings": [],
    "evidence": []
  },
  "missing_evidence": [],
  "required_remediation": []
}
```

Do not average the dimensions or award partial credit. Run the bundled
deterministic grader after this semantic review to catch structural and
chronological inconsistencies. If an independent evaluator is unavailable or
forbidden by repository instructions, perform this as an explicit second-pass
audit and disclose that it was not independent.
