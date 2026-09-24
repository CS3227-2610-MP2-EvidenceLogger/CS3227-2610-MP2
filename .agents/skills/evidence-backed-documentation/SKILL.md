---
name: evidence-backed-documentation
description: Create or update EvidenceLogger developer guides, user guides, release verification, troubleshooting notes, and similar project documentation from current repository evidence and actual check results. Use for documentation work; exclude feature implementation, specification changes, and unsupported release claims.
---

# Evidence-backed documentation

Produce documentation that a reader can trust against the current checkout. Treat approved requirements, implemented behavior, configured automation, and observed verification as different kinds of evidence.

## Establish scope and ownership

Before editing, identify:

- the requested document and whether the task is creation, correction, or review;
- its audience and the decisions or tasks that audience needs it for;
- its owner from the user's assignment, `CODEOWNERS` if present, nearby metadata, and relevant Git history;
- the approved specifications that govern its claims.

Do not modify documentation owned by another developer unless the task includes that work and any needed coordination. If ownership is unclear, inspect the evidence and report the ambiguity rather than claiming authority. Do not change specifications, production code, tests, build configuration, migrations, or CI as part of a documentation-only task.

## Inspect the current repository

Read `AGENTS.md` and the relevant approved files under `specs/` first. Then inspect the portions of the current checkout that can substantiate the document, including as applicable:

- source code and runtime resources for implemented screens, services, workflows, errors, and prerequisites;
- `build.gradle`, `settings.gradle`, wrapper configuration, and the live Gradle task list;
- `.github/workflows/` and repository check scripts;
- application-data path resolution and startup wiring;
- migration runners, versioned SQL resources, schema tests, and seed behavior;
- logging initialization, handlers, destinations, rotation, fallback, and redaction behavior;
- unit, integration, acceptance, and packaging tests;
- existing documentation, links, examples, conventions, and generated-site boundaries.

Absence is evidence too. A package name, interface, placeholder screen, package comment, specification, or CI plan does not prove that the behavior is implemented. If path resolution, migrations, logging, a screen, or a workflow is not present in executable code/resources, describe it as missing, deferred, or specified but not implemented as appropriate.

Use fast targeted searches before broad reading. Do not rely on generated documentation output when an editable source exists.

## Apply the evidence hierarchy

Base claims on the strongest available evidence:

1. Current executable source, configuration, and resources establish what this checkout can do.
2. A command executed in the current task establishes only its recorded result in the recorded environment.
3. Tests establish the behavior they actually exercise. Test names or unexecuted test code do not establish a passing result.
4. CI configuration establishes what CI is configured to attempt. It is not evidence that a particular run passed.
5. Approved specifications establish required, approved, or deferred behavior. They do not establish implementation.
6. Existing documentation is a lead to verify, not an independent source of truth.

When sources disagree, prefer current implementation for descriptions of present behavior, keep the approved specification authoritative for intended scope, and call out the discrepancy. Never turn a plan, interface contract, TODO, proposed architecture, or unexecuted command into a completed-feature claim.

## Match the audience

- **User guides:** describe only visible, implemented actions in the order the actual screens support. State prerequisites and limitations plainly. Avoid internal packages, tables, hashes, and service mechanics unless needed for a safe user task.
- **Developer guides:** explain verified architecture, setup, runtime paths, persistence, logging, build tasks, and extension points at implementation depth. Clearly distinguish implemented code from approved design.
- **Release verification:** identify the exact artifact or commit, command, environment, date when useful, and result. Separate automated checks from manual smoke tests and list failures, skips, and gaps.
- **Troubleshooting notes:** connect an observed symptom to implemented diagnostics and recovery steps. Do not invent log locations, database locations, configuration switches, or recovery guarantees.

Keep developer internals out of user instructions when they do not help the user complete or recover a task. Keep operational prerequisites and known limitations out of purely architectural prose only when another clearly linked document owns them.

## Verify commands, paths, workflows, and examples

- Derive build and run commands from the wrapper and existing Gradle tasks. When authorized, use `./gradlew tasks --all` or the platform-equivalent wrapper command to reject stale or invented task names.
- Match every filesystem path to current path-resolution code. A path proposed in a specification must be labeled proposed until implemented and tested.
- Match UI instructions to implemented screens and match behavioral claims to concrete service implementations, not just interfaces or DTOs.
- Match database instructions to actual migration resources, migration-runner behavior, and supported schema transitions.
- Match logging instructions to the active logging setup and its real output location. Do not confuse diagnostic logs with append-only audit history.
- Check local links, headings/anchors, referenced files, copied commands, and examples. For external links, verify them when network access is authorized; otherwise label link verification as not run.

Run relevant documentation, build, test, packaging, or smoke-test commands when authorized and proportionate to the claims being changed. Do not implement a missing feature merely to make documentation true. If a command cannot be run, preserve the reason and narrow or qualify the claim.

## Report verification honestly

For each result, record enough context to interpret it: exact command, exit status or observed outcome, operating system, architecture when relevant, Java/runtime version, and artifact when relevant. Do not generalize beyond that environment.

Keep these categories separate:

- **Automated:** commands and CI runs actually executed, with pass, fail, or skipped status.
- **Manual smoke:** user-visible steps actually performed against a named artifact/environment, with observations.
- **Not verified:** checks not run, unavailable environments, or unsupported/untested platforms.

A successful build on Windows does not verify launch behavior on macOS or Linux. A CI matrix entry does not by itself prove a release-JAR smoke test. Label every untested OS/CPU combination as unverified; do not infer support from dependency declarations or an all-platform artifact name.

Clearly label prerequisites, limitations, known failures, skipped checks, unsupported or untested platforms, and deferred features. Never hide a failed check behind an overall success statement.

## Protect sensitive information

Never expose or copy secrets, tokens, environment values, plaintext passwords, password hashes or salts, full credential records, or sensitive free text such as examination notes, request purposes, correction reasons, and hold comments. Use fictional redacted examples where an example is necessary.

Approved fictional demo credentials may appear only in their intended user documentation, must be labeled demo-only, and must be confirmed from the implemented seed behavior. Do not place them in developer guides, release reports, troubleshooting output, logs, tests copied into docs, or the final task report.

## Review the final diff

Before finishing, inspect the documentation diff and confirm that:

- only assigned documentation and required task reporting changed;
- each present-tense feature claim has implementation evidence;
- commands and paths match the current checkout;
- user workflows match implemented screens and services;
- automated and manual results are separated and environment-specific;
- failures, skips, untested platforms, prerequisites, limitations, and deferred work remain visible;
- links, examples, and headings are valid to the extent checked;
- no secret or sensitive free text was introduced;
- generated documentation output was not hand-edited in place of its source.

## Expected report

Report:

- documents changed and their intended audience;
- principal repository evidence used and any specification/implementation mismatch;
- commands and link/example checks actually run, with environment and result;
- automated results separately from manual smoke tests;
- known failures, skipped checks, unverified platforms, deferred features, and ownership blockers;
- anything deliberately not changed because it was out of scope or owned elsewhere.

## Definition of done

The task is done when the assigned documentation reflects current executable behavior at the right audience level; commands, paths, workflows, links, and examples have been checked as far as authorized; verification statements identify actual environments and outcomes; unsupported claims and sensitive data are absent; gaps and unverified platforms are explicit; the final diff is scoped; and the report gives the evidence and honest verification status. A required check that fails remains a reported unresolved issue, not a pass.
