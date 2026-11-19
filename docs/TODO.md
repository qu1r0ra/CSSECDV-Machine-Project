# Submission readiness

This file tracks evidence against the [specification](specifications.md) and [checklist](checklist.md). GitHub Issues own implementation scope, assignments, and discussion. Add issue links as work is allocated. Mark an item complete only when its evidence is recorded here or linked from its issue; starter code and template files alone do not establish compliance.

## Setup

- [x] Import the editable course starter and retain source/form pairs.
- [x] Convert both handouts into cleaned Markdown references.
- [x] Prepare the required submission-document templates.
- [x] Verify the complete Ant build and distribution contents. Setup evidence (2026-10-22): JDK 21.0.12.1 and Ant 1.10.18 compiled all 18 Java sources with `ant -f app/build.xml clean init-db jar`. The distribution contains the executable JAR, JDBC dependency, and database. A temporary launcher loaded the JDBC driver through the JAR manifest, queried the packaged database, and initialized/closed the application frame from a separate directory. Original Java 8 option warnings and an unchecked-operation warning in `MgmtUser.java` remain. Security behavior has not been verified.

Readiness recheck (2026-11-02): JDK 21.0.13 and Ant 1.10.18 rebuilt all 18 starter sources with `ant -f app/build.xml init-db jar`. A temporary standalone distribution passed JDBC loading through the JAR manifest, a SQLite product query and Swing frame construction without showing a window. The fixture matched HEAD and remained unchanged. Existing Java 8 target and unchecked-operation warnings remain. These checks establish build/package readiness, not security behavior; the initial recheck preceded the Bundle B behavior suite below.

## Bundle B implementation evidence

Candidate implementation evidence (2026-11-02): `ant -f app/build.xml clean init-db jar test-bundle-b` passed with JDK 21.0.13 and Ant 1.10.18. The clean build compiled all 19 application sources, packaged the executable JAR and SQLite dependency, and ran `Controller.BundleBServiceTest` against real temporary SQLite databases. Checks cover the full B role matrix and direct service calls, client history scope, operation/target/input-bound re-authentication, session-generation changes and provider failures, maximum field and search boundaries, exact money, legacy price migration, immutable purchase snapshots after edit/delete, canceled/replayed quotes, competing last-unit quotes, timestamped rejection/failure audits, rollback and safe fallback for failed add/edit/delete/purchase audits. The fixture remained unchanged. Java 8 target warnings and the existing `MgmtUser.java` unchecked-operation warning remain.

Review-repair evidence (2026-11-02): the Standards/Spec review gaps are covered by new checks in `Controller.BundleBServiceTest`: leading-zero price input (`00000001.5` accepted as exact minor units), an audited rejected quote for insufficient stock, stale confirmation after a price change (fresh confirmation required), and a forced `purchase_records` insert failure that rolls back stock and history and falls back safely. Red-first was observed only for leading-zero price and insufficient-stock audit (both red against the HEAD service, green after the repair). The changed-price and snapshot-failure checks already passed at HEAD and are recorded as coverage additions, not as red-first fixes. The same clean Ant command passed after the repair.

Targeted re-review (2026-11-02, committed head 2863279): Standards (Claude Haiku 5.5, high) and Spec (Claude Sonnet 5.5, medium) reported no P1 findings. Spec P2s were repaired in a follow-up commit: the purchase handler now reloads the catalog after a conflict, a true simultaneous duplicate submission is checked, and stale quotes after a stock change or deletion are checked. Standards P3s removed redundant UI pre-checks; validation and invalid-attempt audit now sit only in the service. The follow-up was rebuilt and rechecked with the clean Ant command but was not re-reviewed, per the one-re-review budget. Two Standards P2s outside Bundle B were residual at this review: `SQLite.addUser` built SQL by string concatenation (checklist 2.3.4) and `MgmtUser` printed entered passwords to the console (checklist 2.1.3/2.1.7). PR #12 fixed both (see the shared-layer fix below).

Landing evidence (2026-11-02): PR #10 was squash-merged from head `0e1eafd` (the verified implementation tip; GitGuardian SUCCESS) to `main` as `47138fe`. The merged tree matches `0e1eafd` exactly. The Bundle B worktree was removed, and the local branch `feat/6-bundle-b` was deleted after verifying it matched the merged head. The remote branch `origin/feat/6-bundle-b` was then deleted as well.

Shared-layer fix (2026-11-02, PR #12, squash-merged from head `6694e27` to `main` as `4208f6d`): `SQLite.addUser` (both overloads) now uses PreparedStatements, and `MgmtUser` no longer prints entered passwords. This closes the two residual findings noted above. A scratch-database round trip stored a quoted password and an injection-shaped username as literal values, and the clean Ant build with `test-bundle-b` passed. Other concatenated queries in `SQLite.java` (`addHistory`, `addLogs`, `addProduct`, `removeUser`, and `getProduct(name)`) remain until C's rewrite on `chore/prac_exec` lands.

The status recorded above predates the Bundle A merge and is superseded by the source review below. On current `main` (`22a690f`, 2026-11-19), `AuthService`, `AccessControl`, `UserAdminService`, and `AppSecurityGateway` are present and wired by `Main`; the old claim that the app's adapter denies all B operations is no longer accurate. This source review is not a test result. [Integration issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9) remains open for integrated acceptance, manual Swing scenarios, shared logging/database cleanup, and full submission evidence.

## Assignment status snapshot (2026-11-19)

The agreed ownership below is from [team allocation issue #4](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4). Status reflects the code and evidence recorded on `main` at `22a690f`; it does not replace acceptance checks or declare a project-wide requirement complete. The original [course checklist](checklist.md) remains the source of scored criteria. Its boxes below stay unchecked until the required evidence is recorded.

| Assignment | Owner | Specification scope | Status on current `main` | Remaining work |
| --- | --- | --- | --- | --- |
| **A — Authentication and credentials** | Liam Michael Alain Ancheta | Registration, login/logout and session; password hashing/policy/masking; five-failure lockout; last-login status; re-authentication; credential storage. Primarily checklist 2.1.1–2.1.10, plus validation and audit for A-owned inputs/events. | Core code is present in the Bundle A merge (`22a690f`): `AuthService`, `AccountStore`, `PasswordHasher`, `InputValidator`, A behavior tests, and Swing login/registration wiring. The A test target exists, but this tracker has no recorded run after the merge. | Run and record A checks against the integrated revision; manually verify login/register/logout, masking, last-attempt display, lockout, re-authentication, and session revocation. Persistent auth audit is not connected (`Main` supplies `SecurityAudit.NONE`). Resolve whether re-auth failures should count toward the checklist's five failed **login** attempts; current code counts them. Prepare compliant demo accounts and record their use. |
| **B — Products, purchases and history** | Christian Joseph Bunyi | Product add/edit/delete; Client purchasing and own history; Manager all-user history; related UI/database operations. Primarily checklist 2.2.2–2.2.3, 2.3.1–2.3.4, 2.4.1–2.4.4, and B contributions to 2.5.2–2.5.4. | B service, Swing callers, B-only ACL/docs/demo cases are present. Prior Bundle B service checks against temporary SQLite are recorded above; those checks used controlled shared-provider doubles and do not establish integrated acceptance. | Rerun relevant B checks with the actual A provider; perform and record the Swing walkthrough; verify audit events appear in the Admin log view. Complete system-wide documentation/matrix/demo evidence rather than only B rows. |
| **C — Shared authorization, Admin tools, logging and delivery** | Roan Cedric Campo | Central authorization/navigation; Admin user/role management and account re-enabling; audit storage and Admin log management; shared validation/errors/fallback; integration and packaging. Primarily checklist 2.2.1–2.2.4, shared 2.3–2.5, and account/package deliverables. | Current code contains `AccessControl`, role routing, `UserAdminService`, and a Bundle B gateway backed by A services. Some C scope is therefore present, but shared audit persistence and the legacy database layer are incomplete. | Wire a persistent audit writer; finish or retire unsafe legacy `SQLite` methods; finish Admin log management; verify account re-enabling and admin-only access; complete shared failure/fallback review and prepared standalone packaging. Reconcile C's delivered revision with current `main` through #9. |
| **Integration and final acceptance** | Christian (integration owner, per #9); coordinate with Liam and Roan | Reconcile A/B/C interfaces, run actual-provider integration and manual scenarios, update shared docs, prepare and verify final distribution. | [Issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9) remains open. The Bundle A code is now on `main`; prior notes saying A was absent are stale. | Verify direct calls and UI against all role rules; persistent logs; schema/data preservation; clean build and behavior checks; manual walkthrough; full ACL/security documentation/demo cases; compliant demo database and standalone distribution. Record exact revisions and results before checking scored items complete. |

### Current cross-bundle gaps

- **Audit persistence:** [Main.java](../app/src/Controller/Main.java) injects `SecurityAudit.NONE`, so A's authentication/validation/authorization events are discarded. B's service-level audit behavior does not fill the shared A/C logging requirement.
- **Legacy SQL and error handling:** [SQLite.java](../app/src/Controller/SQLite.java) still concatenates values in `addHistory`, `addLogs`, `addProduct`, `removeUser`, and `getProduct(name)`; several methods print exception details, and `getLogs()` prints a stack trace. Newer A/B stores use parameterized queries, but this does not satisfy the project-wide requirement while these legacy paths remain.
- **Admin log management:** [MgmtLogs.java](../app/src/View/MgmtLogs.java) gates log access by role, but its Clear handler is empty. Verify and complete the required Admin management behavior with a retention-safe policy.
- **Demo delivery:** [DemoAccountSeeder.java](../app/src/Controller/DemoAccountSeeder.java) can prepare role accounts, but no prepared demo database is recorded. The standard package target uses the vulnerable starter fixture; the final distribution must use a prepared database and be verified independently.
- **Evidence and documents:** the security documentation, ACL, and demo cases currently describe Bundle B rather than the complete application. Update them per bundle and record executed end-to-end evidence.

## Accounts and security controls

Each row maps to the exact requirement in [checklist.md](checklist.md). `Ready` is the final evidence-backed completion checkbox; `Current status` reports implementation evidence and remaining verification. A code path or test source is not a passing test result. Owners are grouped by the agreed A/B/C split; shared requirements have multiple owners.

| Ready | Checklist ID | Owner | Current status and remaining evidence |
| --- | --- | --- | --- |
| [ ] | 1.1.1 | A + C | Open — seeder supports a Role 5 account; a prepared, usable demo database and successful login evidence are not recorded. |
| [ ] | 1.1.2 | A + C | Open — seeder supports Staff/Manager accounts; database preparation and role walkthrough are not recorded. |
| [ ] | 1.1.3 | A + C | Open — registration assigns Client and seeder supports Client accounts; prepared-demo evidence is not recorded. |
| [ ] | 2.1.1 | A + C + B | Code present — AuthService and protected operation gates exist; verify every protected panel/resource through direct calls and Swing. |
| [ ] | 2.1.2 | A + C + B | Partial — service paths deny on authentication/storage failures; verify whole-app fail-closed behavior and fallback. |
| [ ] | 2.1.3 | A | Code present — salted PBKDF2 and legacy-password migration exist; prepare/inspect the actual distribution database to establish no plaintext credentials remain. |
| [ ] | 2.1.4 | A | Code present — login returns a generic failure; run the behavior checks and verify messages in Swing. |
| [ ] | 2.1.5 | A | Code present — registration enforces upper/lower/digit/symbol; execute boundary and rejection checks. |
| [ ] | 2.1.6 | A | Code present — 8–64 character policy; execute boundary checks and record evidence. |
| [ ] | 2.1.7 | A + B + C | Code present — login/register/current-password inputs use masked controls; manually inspect every password prompt. |
| [ ] | 2.1.8 | A | Partial — fifth counted failure sets Role 1, but re-authentication failures share the counter. Confirm or adjust against “five consecutive failed login attempts”; run exact-boundary checks. |
| [ ] | 2.1.9 | A | Code present — successful login displays prior attempt timestamp/status; verify in Swing with success and failure histories. |
| [ ] | 2.1.10 | A + B + C | Code present — sensitive admin and catalog operations request fresh credentials; verify password-change and all critical-operation paths end to end. |
| [ ] | 2.2.1 | C + B | Code present — AccessControl and B operation-level authorization exist; verify every protected feature and direct service call. |
| [ ] | 2.2.2 | A + B + C | Partial — role rules exist in services and views; finish full-feature coverage and manual UI visibility checks. |
| [ ] | 2.2.3 | A + B + C | Partial — new services fail closed, but legacy database/error paths and integrated failure cases remain. |
| [ ] | 2.2.4 | C + A | Code present — Frame routes to role-specific homes; verify all roles and disabled/unknown roles in Swing. |
| [ ] | 2.3.1 | A + B + C | Partial — strict validators exist for account and B fields; audit all remaining Admin/shared inputs and verify total rejection. |
| [ ] | 2.3.2 | A + B + C | Partial — role, stock, quantity and price bounds exist; verify all numeric fields, including Admin actions. |
| [ ] | 2.3.3 | A + B + C | Partial — bounds exist for account and B inputs; confirm maximum raw length on every remaining input field. |
| [ ] | 2.3.4 | A + B + C | Partial — AccountStore and BundleBService parameterize their interactions; legacy SQLite methods still concatenate SQL. |
| [ ] | 2.4.1 | A + B + C | Partial — B returns safe messages; legacy SQLite methods print exception details/stack traces. |
| [ ] | 2.4.2 | A + B + C | Partial — newer services use generic outcomes; replace technical output and check every user-facing error path. |
| [ ] | 2.4.3 | A + B + C | Partial — service-level exception handling exists; legacy DB methods swallow or print errors and whole-controller coverage is incomplete. |
| [ ] | 2.4.4 | A + B + C | Partial — Frame has a safe Login fallback and B invokes it for unrecoverable errors; verify all controller/database failure paths. |
| [ ] | 2.5.1 | C | Partial — log viewing is Role 5-gated; Clear is a no-op and management behavior is not complete/verified. |
| [ ] | 2.5.2 | A + B + C | Partial — B records timestamped events; shared A/C audit is discarded by SecurityAudit.NONE. |
| [ ] | 2.5.3 | A + C | Open — AuthService emits events to the audit interface, but Main wires the no-op implementation. |
| [ ] | 2.5.4 | B + C | Partial — B logs its denials; AccessControl sends shared denials to the no-op audit implementation. |

## Required functionality and deliverables

- [ ] Verify registration, account re-enabling, and all role-authorized operations against the [role descriptions](specifications.md#authorization), including Admin/Disabled and direct-call cases.
- [ ] Complete [security documentation](security-documentation.md) for every addressed checklist item, distinguishing vulnerability, impact, and exact control.
- [ ] Complete the [access control matrix](access-control-matrix.md) for every feature/resource and verify least privilege.
- [ ] Complete [demo test cases](demo-test-cases.md) covering all five control groups and record execution evidence.
- [ ] Prepare the required functional demo accounts in a database with compliant credential storage; run and record the seeder, then inspect the packaged database.
- [ ] Package the complete source folder and standalone distribution: executable JAR, all dependencies, and prepared database.
- [ ] Verify the distribution launches and works from a separate directory using its own prepared database and dependencies; record the exact build/revision and scenarios.

Optional compatibility follow-up: the specification asks the team to try the finished project in NetBeans. Development uses the JDK and Ant; NetBeans is not installed as part of setup.

## Scheduling and later decisions

The October 20 class announcement identified **November 20, 2026** as the first demo date, with November 24 or 27 mentioned for later sessions. Our assigned slot and a common final code-submission deadline are not established by these handouts. Treat the earliest date as a preparation target until the assigned schedule is confirmed. The same announcement said no PowerPoint was required.

- [ ] Confirm our assigned demo slot and final submission deadline when announced.
- [x] Agree on Bundle A/B/C ownership and scope; see [GitHub Issue #4](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4). Create implementation issues with shared interfaces and dependencies before parallel work.
- [x] Settle and publish [Bundle B specification #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6), including the confirmed testing seam and A/C dependencies. Publication evidence (2026-11-02): the live body matches the settled specification, all seven template sections and 24 user stories are present, and `ready-for-agent` is applied. This is planning evidence, not implementation compliance.
- [x] Approve and publish the [B-only implementation plan](bundle-b-spec.md): [catalog #7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) precedes [purchases/history #8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8). Publication evidence (2026-11-02): verified exact live bodies with 22 acceptance criteria (including the readiness audit's history-search validation/ownership clarification), `ready-for-agent` labels, Christian's self-assignment, native sub-issues of #6, #8 blocked by #7 and the matching approved map in #6. Implemented and landed in [PR #10](https://github.com/qu1r0ra/CSSECDV-Machine-Project/pull/10) as `47138fe` (2026-11-02); [#7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) and [#8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8) are closed. A/C integration remains the acceptance gate in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).
- [x] Password length bounds: 8 minimum and 64 maximum characters, following common guidance (NIST SP 800-63B). The checklist requires bounds but does not give values. `InputValidator` on current `main` enforces the bounds; behavior evidence still needs to be run and recorded against the integrated revision.
- [ ] Reconcile Roan's Bundle C work with current `main`; the implementation/integration gate remains tracked in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9). Prior notes saying all A/C providers were absent are superseded by Bundle A commit `22a690f`; shared logging, legacy SQLite cleanup, integration and packaging remain open.
