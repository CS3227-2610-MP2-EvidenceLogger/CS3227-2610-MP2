# EvidenceLogger project instructions

- Build a Java SE 25 desktop application with two roles:
  Evidence Custodian and Investigator.

- Do not reuse MP1 code or artifacts.

## Architecture
- Preserve separation between user-role UIs.
- Shared logic must not be duplicated between roles.
- Follow SRP and DRY.
- Do not introduce a new abstraction unless at least two consumers need it.

## Agent workflow
- Use one agent. Do not delegate work to sub-agents.
- During specification work, distinguish confirmed requirements,
  proposed decisions, and open questions.
- During implementation, read the relevant approved specifications
  in `specs/` and existing code first. Implement only the assigned
  task and avoid unrelated refactoring.
- For implementation requests, make the required code changes;
  do not stop at a plan unless planning was explicitly requested
  or a blocking decision remains unresolved.
- Avoid unrelated refactoring.
- Add tests for behaviour changes.
- Run relevant tests after every logical change.
- Add or update automated tests for changed application behavior.
  Run the relevant available checks before declaring completion.
- Report actual verification results, including failed or skipped
  checks. Do not claim that unexecuted checks passed.
- Do not modify agent instructions or skills during ordinary
  feature work. Propose improvements separately.

## Safety
- Do not run destructive git commands.
- Do not commit, push, or publish to master unless requested.
- Do not add dependencies without approval.
- Ask before modifying shared architecture used by another role.
- Do not invent unresolved business rules. If an ambiguity affects
  application behavior, explain the decision needed and ask for
  clarification. Continue independent work where possible.

## Testing
- Ensure `./gradlew clean check` passes with no errors before declaring a task complete.

## Reflection / logging
- At the end of every substantial agent-assisted task (in a chat, not just in a prompt),
  produce a summary under `logs/${github_user_id}/`.
- The logs should be created under the respective human's GitHub account username's directory
- The summary must distinguish:
  - user instructions
  - agent actions
  - human corrections
  - tests/results
  - unresolved issues
- The log file should follow this naming convention: `YYYY-MM-DD-<short-task-description>.md`

You may use the following template, and fill up all sections except `Human intervention`, `Observations`,
`Reflection Note`, and `Verification of summary`:
```markdown
# Agent Interaction Summary

## Metadata
Date:
Developer:
Skill(s) used:
Commit:

## Task
What was the agent asked to do?

## Important prompts
Summarise the important instructions/prompts given to the agent.

## Agent actions
- Files inspected:
- Files modified:
- Commands/tests run:
- Major implementation decisions:

## Human intervention
What did I have to correct, clarify, reject, or redo?

## Observations
Examples:
Agent saved significant time
Agent wrote mostly correct code but required one correction
Agent misunderstood role access
Agent caused a regression
Agent over-refactored
Agent hallucinated an API
Agent produced weak tests
Agent spotted a bug the team missed
Agent review produced false positives
Improving SKILL.md fixed recurring behaviour
Agent misunderstood a requirement
Agent missed an edge case
Agent created incorrect code
Agent saved time or caused additional work

## Verification
Tests run:
Result:
Manual checks:

## Reflection note
Effectiveness: High / Medium / Low

What was useful?

What would I change about the skill/instructions next time?

## Verification of summary
Reviewed by: PENDING
```
