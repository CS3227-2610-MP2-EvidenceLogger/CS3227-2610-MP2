---
name: application-composition
description: Wire or rewire existing EvidenceLogger components at the application composition root, including startup, authenticated role routing, logout, and deterministic resource shutdown. Use for incremental integration or final assembly; do not use to implement missing features, invent adapters, or change domain policy.
---

# Application Composition

Connect existing implementations into one explicit EvidenceLogger object graph while preserving dependency direction, role separation, and resource lifecycle. Keep the composition root limited to construction, configuration, navigation, startup sequencing, and teardown; business rules remain in services and the domain.

## Scope and boundaries

Use this skill when a task adds, replaces, fixes, reviews, or completes wiring among existing connection factories, migration runners, repositories, transaction services, clocks or ID sources, session management, application services, role views, the launcher, the application shell, startup, logout, or shutdown. It applies to both a small incremental integration and final application assembly.

Do not use it to:

- Implement a missing feature, repository, service, migration, view, or business rule.
- Add speculative adapters, placeholder implementations, hidden fallbacks, service locators, global mutable state, or a dependency-injection framework.
- Change domain policy, authorization behavior, transaction semantics, or role responsibilities.
- Redesign unrelated interfaces or resolve a cross-workstream contract conflict without approval.
- Refactor outside the composition and lifecycle path needed by the requested integration.

## Required inspection

Before editing, read:

1. `AGENTS.md` for repository workflow, ownership, safety, verification, and reporting requirements.
2. `specs/architecture.md`, especially dependency direction, package responsibilities, UI/service boundaries, authentication, transaction ownership, migration startup, testing, and shutdown guidance.
3. `references/work-split-commit-plan.md` for workstream ownership and shared-contract boundaries.
4. The current build files and wrapper configuration to learn the actual launcher, dependencies, Java/JavaFX versions, test tasks, and repository-wide check.
5. `EvidenceLoggerLauncher`, `EvidenceLoggerApplication`, `ApplicationShell`, and their tests, including any startup/navigation tests.
6. Every constructor, factory method, and interface on the requested wiring path, together with the nearest implementation and focused tests.
7. Session-management and login code plus both `ui.custodian` and `ui.investigator` packages whenever authentication, routing, logout, shared services, or final assembly is involved.
8. Current repository status and relevant branches or merge state when a named component may belong to another workstream.

If a listed artifact does not exist, record that fact in the inventory. Absence is evidence; do not replace it with an assumption.

## Dependency and lifecycle inventory

Produce an inventory before the first edit. For every component on the path, record:

- The concrete implementation available in the current worktree and the interface or constructor it satisfies.
- Its direct dependencies and whether each dependency is present, compatible, and already wired.
- Its owner and lifetime: per call, per view/controller, per authenticated session, or application-wide.
- Whether identity matters across consumers, such as one session manager, transaction service, clock, ID source, executor, connection factory, or navigation shell.
- Its startup prerequisite, teardown obligation, and teardown order when it owns threads, connections, listeners, or other state.
- Which role or roles consume it and through which service boundary.
- The focused tests that prove its contract or lifecycle, and any expected test that is absent.

Classify each problem before changing code:

- **Wiring defect:** Compatible implementation and dependency contracts exist, but construction, instance reuse, sequencing, routing, or teardown is missing or wrong. Fix this in scope.
- **Missing implementation:** A required concrete component does not exist in the current worktree. Stop the affected path and report a blocker; do not fabricate behavior, add a fake to production, or silently omit the feature.
- **Incompatible contract:** The producer cannot satisfy the consumer's current constructor or interface without changing a shared contract or inventing translation behavior. Show the mismatch and request approval from the relevant owner before changing the contract.
- **Unavailable or unmerged component:** Evidence indicates that another workstream owns it or it exists only on an unmerged branch. Report the branch/component and limit work to independent wiring that can be verified locally.

## Composition procedure

1. Define the integration slice.
   - State whether this is incremental wiring or final assembly.
   - Identify the entry point, user-visible lifecycle path, and expected representative service call.
   - Separate confirmed requirements, proposed wiring decisions, and unresolved cross-workstream questions.

2. Draw the object graph from stable infrastructure inward to UI consumers.
   - Preserve the approved direction: UI depends on application services; services depend on domain and repository interfaces; JDBC implementations satisfy repository interfaces and depend on database infrastructure.
   - Construct dependencies explicitly and pass them through constructors or existing factories.
   - Keep SQL, authorization decisions, transition rules, validation policy, and workflow branching out of the composition root.
   - Do not let controllers construct repositories, open connections, or select service implementations.

3. Assign ownership and reuse deliberately.
   - Create one application-wide instance where identity or coordinated lifecycle matters, including the service-owned current session and shared stateful resources.
   - Reuse the same instance for every consumer that must observe the same state; do not accidentally create separate session managers, clocks, ID sources, executors, transaction coordinators, or shells.
   - Create short-lived objects only when their contract makes that lifetime explicit.
   - Record the owner responsible for closing each closeable or stopping each executor. Do not rely on garbage collection, daemon-thread exit, or UI disappearance.

4. Enforce startup order.
   - Resolve application data paths and initialize required diagnostics as approved.
   - Construct the connection factory and migration support.
   - Run migrations successfully before exposing the login view or accepting authentication.
   - On startup or migration failure, keep protected views unavailable, show a readable failure through the existing presentation boundary, retain diagnostic detail in the approved logging path, and avoid continuing with a partly understood schema.
   - Only after startup prerequisites succeed, construct or expose login/session-driven navigation.

5. Wire authentication, routing, and logout.
   - Use the one service-owned session source used by protected services; never treat navigation state as authorization.
   - After successful authentication, route `CUSTODIAN` to the Custodian top-level view and `INVESTIGATOR` to the separate Investigator top-level view.
   - Do not merge role UIs into a single role-switching controller or expose one role's view as a fallback for the other.
   - Make unsupported or unexpected role state an explicit failure rather than defaulting to a privileged view.
   - Logout must clear the current session before returning to login, detach or replace session-scoped views/listeners as needed, and prevent retained controllers from acting with prior identity.

6. Wire services without changing their behavior.
   - Bind each consumer to an existing compatible implementation through its published interface.
   - Pass the same transaction runner, authorization/session source, clock, ID sources, and audit writer wherever their shared identity or atomic lifecycle is required.
   - Keep one command's transaction boundary inside its application service and transaction service; the composition root does not begin, commit, retry, or recover business transactions.
   - For an incremental integration, leave unrelated available components alone. For final assembly, inventory every required role path and report any gap rather than weakening the object graph.

7. Make shutdown deterministic.
   - Stop accepting new UI work, clear the current session, detach owned observers, stop executors, and close other owned resources in an order consistent with their dependencies.
   - Await or otherwise verify termination where the existing contract supports it; preserve interruption and report failures through existing diagnostics.
   - Make repeated or partially initialized shutdown safe. Release only resources owned by the application composition root.

8. Review the diff before verification.
   - Confirm there is no new business logic, policy, persistence behavior, global state, service locator, hidden fallback, speculative stub, dependency, or unrelated interface redesign.
   - Confirm every stateful resource has one documented owner, intended lifetime, and teardown path.
   - Confirm missing or incompatible components remain explicit blockers rather than production fakes.

## Verification

Add or update the narrowest tests appropriate to the wiring change. Exercise behavior, not only constructor coverage:

- Successful startup and migration completion before login becomes usable.
- Startup or migration failure reporting without exposing authenticated views or continuing startup.
- Successful authentication routes each role to its separate top-level view.
- Logout clears the shared session and returns to login; retained protected UI cannot continue as the prior actor.
- At least one representative end-to-end application-service call per newly connected role or service path, using the real wired collaborators at the deepest practical boundary.
- Deterministic shutdown of every newly owned resource, including partially initialized and repeated shutdown when applicable.
- Instance-identity assertions where accidentally duplicating a session manager, transaction service, clock, ID source, executor, or other stateful dependency would break behavior.

Prefer stable service/composition tests over fragile headless JavaFX automation. Use a small seam around composition or navigation only when at least two real consumers or tests justify it under the repository's abstraction rule. Do not add a testing dependency without approval.

Run focused tests after each logical change. Before declaring completion, run the repository-required clean check from `AGENTS.md` using the checked-in platform wrapper, currently `./gradlew clean check` or its platform equivalent. Report the exact commands, passes, failures, skips, unavailable UI checks, and remaining unverified behavior. A failed or unavailable clean check prevents claiming done.

## Final report contract

Report:

- The integration slice completed and whether it was incremental wiring or final assembly.
- The dependency/lifecycle inventory, summarized by component, implementation, owner, lifetime, reuse, startup prerequisite, and teardown.
- Files changed and the object-graph, startup, routing, logout, or shutdown behavior affected.
- Every wiring defect fixed; every missing implementation, incompatible contract, unavailable/unmerged component, and approval still needed.
- Focused and repository-wide verification commands actually run, with pass, fail, skipped, and unavailable results.
- Manual checks still required, especially JavaFX or platform checks not executed.
- Any approved deviation from the architecture or work split; otherwise state that none was introduced.

Do not describe a blocked path as implemented, an unexecuted check as passed, or a locally absent component as available.

## Definition of done

- [ ] The required repository, architecture, work-split, build, launcher/shell, constructor/interface, test, session, and relevant role-package inspection occurred before editing.
- [ ] A dependency and lifecycle inventory was recorded before editing, including absent tests or components.
- [ ] Every issue was classified as wiring, missing implementation, incompatible contract, or unavailable/unmerged work.
- [ ] Only existing, compatible components were wired; no feature, policy, speculative adapter, production fake, hidden fallback, or unapproved shared-contract change was introduced.
- [ ] Dependency direction remains inward and the composition root contains no business rules, SQL, authorization policy, or transaction control.
- [ ] Stateful resources have explicit ownership and lifetime, identity-sensitive dependencies are shared intentionally, and shutdown is deterministic and safe after partial startup.
- [ ] Migrations complete before login is usable; startup failure leaves protected application behavior unavailable and is reported readably.
- [ ] Authenticated Custodians and Investigators route to separate views, and logout clears the shared session before returning to login.
- [ ] Tests cover the changed startup, failure, role-routing, logout, representative service-call, identity, and shutdown behavior where applicable.
- [ ] Focused checks pass and the repository clean check succeeds; otherwise the exact failure and unverified behavior are reported and completion is not claimed.
- [ ] The final report satisfies the contract and names all blocked, unavailable, skipped, or manual work honestly.
