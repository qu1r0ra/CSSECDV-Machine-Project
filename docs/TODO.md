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

The B service API, Swing caller wiring, security documentation, B access matrix, and demo scenarios are present. Actual A/C identity/session and authorization providers are not available on this base branch; [integration issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9) remains a gate. The app's current adapter denies all B operations. Manual Swing visibility and end-to-end account walkthroughs have not been performed, and the project-wide checklist below remains pending.

## Accounts and security controls

Each row maps to the exact requirement in [checklist.md](checklist.md); short labels below are navigation aids. All compliance evidence is initially pending.

| Ready | Checklist ID | Area | Issue / evidence |
| --- | --- | --- | --- |
| [ ] | 1.1.1 | Administrator account | Pending |
| [ ] | 1.1.2 | Staff/Manager accounts | Pending |
| [ ] | 1.1.3 | Customer account | Pending |
| [ ] | 2.1.1 | Authentication for protected resources | Pending |
| [ ] | 2.1.2 | Authentication fails securely | Pending |
| [ ] | 2.1.3 | Strong one-way salted password hashes | Pending |
| [ ] | 2.1.4 | Generic authentication failures | Pending |
| [ ] | 2.1.5 | Password character policy | Pending |
| [ ] | 2.1.6 | Minimum and maximum password length | Pending |
| [ ] | 2.1.7 | Masked password inputs | Pending |
| [ ] | 2.1.8 | Lockout after exactly five consecutive failures | Pending |
| [ ] | 2.1.9 | Last-use timestamp and status | Pending |
| [ ] | 2.1.10 | Re-authentication for critical operations | Pending |
| [ ] | 2.2.1 | Central authorization component | Pending |
| [ ] | 2.2.2 | Role rules in logic and UI | Pending |
| [ ] | 2.2.3 | Authorization fails securely | Pending |
| [ ] | 2.2.4 | Role-specific home panels | Pending |
| [ ] | 2.3.1 | Total rejection of invalid inputs | Pending |
| [ ] | 2.3.2 | Numeric ranges | Pending |
| [ ] | 2.3.3 | Maximum length for every input | Pending |
| [ ] | 2.3.4 | PreparedStatements throughout database interactions | Pending |
| [ ] | 2.4.1 | Debug information hidden from users | Pending |
| [ ] | 2.4.2 | Generic SQLite/Java error messages | Pending |
| [ ] | 2.4.3 | Controller/database exception handling | Pending |
| [ ] | 2.4.4 | Safe unprivileged fallback panel | Pending |
| [ ] | 2.5.1 | Admin-only log access and management | Pending |
| [ ] | 2.5.2 | Timestamped validation-failure logs | Pending |
| [ ] | 2.5.3 | Timestamped authentication logs | Pending |
| [ ] | 2.5.4 | Unauthorized-access logs | Pending |

## Required functionality and deliverables

- [ ] Verify registration, account re-enabling, and all role-authorized operations against the [role descriptions](specifications.md#authorization).
- [ ] Complete [security documentation](security-documentation.md) for every addressed checklist item, distinguishing vulnerability, impact, and exact control.
- [ ] Complete the [access control matrix](access-control-matrix.md) for every feature/resource and verify least privilege.
- [ ] Complete [demo test cases](demo-test-cases.md) covering all five control groups and record execution evidence.
- [ ] Prepare the required functional demo accounts in a database with compliant credential storage.
- [ ] Package the complete source folder and standalone distribution: executable JAR, all dependencies, and prepared database.
- [ ] Verify the distribution launches and works from a separate directory using its own database and dependencies.

Optional compatibility follow-up: the specification asks the team to try the finished project in NetBeans. Development uses the JDK and Ant; NetBeans is not installed as part of setup.

## Scheduling and later decisions

The October 20 class announcement identified **November 20, 2026** as the first demo date, with November 24 or 27 mentioned for later sessions. Our assigned slot and a common final code-submission deadline are not established by these handouts. Treat the earliest date as a preparation target until the assigned schedule is confirmed. The same announcement said no PowerPoint was required.

- [ ] Confirm our assigned demo slot and final submission deadline when announced.
- [x] Agree on Bundle A/B/C ownership and scope; see [GitHub Issue #4](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4). Create implementation issues with shared interfaces and dependencies before parallel work.
- [x] Settle and publish [Bundle B specification #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6), including the confirmed testing seam and A/C dependencies. Publication evidence (2026-11-02): the live body matches the settled specification, all seven template sections and 24 user stories are present, and `ready-for-agent` is applied. This is planning evidence, not implementation compliance.
- [x] Approve and publish the [B-only implementation plan](bundle-b-spec.md): [catalog #7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) precedes [purchases/history #8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8). Publication evidence (2026-11-02): verified exact live bodies with 22 acceptance criteria (including the readiness audit's history-search validation/ownership clarification), `ready-for-agent` labels, Christian's self-assignment, native sub-issues of #6, #8 blocked by #7 and the matching approved map in #6. Implemented and landed in [PR #10](https://github.com/qu1r0ra/CSSECDV-Machine-Project/pull/10) as `47138fe` (2026-11-02); [#7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) and [#8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8) are closed. A/C integration remains the acceptance gate in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).
- [x] Password length bounds: 8 minimum and 64 maximum characters, following common guidance (NIST SP 800-63B). The checklist requires bounds but does not give values. Enforcement belongs to Bundle A's registration and password-change flows, which are not on `main` yet.
- [ ] Roan's Bundle C implementation on `origin/chore/prac_exec` (`164ad82`) is pending his PR to `main`; the reconciliation and integration owner is tracked in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).
