# Submission readiness

This file tracks evidence against the [specification](specifications.md) and [checklist](checklist.md). GitHub Issues own implementation scope, assignments, and discussion. Add issue links as work is allocated. Mark an item complete only when its evidence is recorded here or linked from its issue; starter code and template files alone do not establish compliance.

## Setup

- [x] Import the editable course starter and retain source/form pairs.
- [x] Convert both handouts into cleaned Markdown references.
- [x] Prepare the required submission-document templates.
- [x] Verify the complete Ant build and distribution contents. Setup evidence (2026-10-22): JDK 21.0.12.1 and Ant 1.10.18 compiled all 18 Java sources with `ant -f app/build.xml clean init-db jar`. The distribution contains the executable JAR, JDBC dependency, and database. A temporary launcher loaded the JDBC driver through the JAR manifest, queried the packaged database, and initialized/closed the application frame from a separate directory. Original Java 8 option warnings and an unchecked-operation warning in `MgmtUser.java` remain. Security behavior has not been verified.

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
| [x] | 2.2.1 | Central authorization component | `Controller/Authorization.java`; see [security-documentation.md](security-documentation.md). Needs a demo pass once Bundle A's login calls `Frame.onLoginSuccess`. |
| [x] | 2.2.2 | Role rules in logic and UI | `View/Frame.java` nav visibility + per-action checks; see security-documentation.md. |
| [x] | 2.2.3 | Authorization fails securely | `Authorization.check` denies null/disabled/mismatched role by default; see security-documentation.md. |
| [x] | 2.2.4 | Role-specific home panels | `Frame.showHomeForCurrentRole`; see security-documentation.md. |
| [x] | 2.3.1 | Total rejection of invalid inputs | `Controller/Validator.java` (boolean-only, no sanitization) wired into `MgmtUser`. Bundle A/B still need to apply it to their own inputs. |
| [x] | 2.3.2 | Numeric ranges | `Validator.isValidRoleCode` (1-5); applied in `MgmtUser` role edits. |
| [x] | 2.3.3 | Maximum length for every input | `Validator` length bounds defined for username/password/product name; only wired where Bundle C has inputs (admin dialogs) so far. |
| [x] | 2.3.4 | PreparedStatements throughout database interactions | All of `Controller/SQLite.java` rewritten; see security-documentation.md. |
| [x] | 2.4.1 | Debug information hidden from users | `SQLite.logError`; UI never receives exception detail, console detail gated by `DEBUG_MODE`. |
| [x] | 2.4.2 | Generic SQLite/Java error messages | Admin dialogs show "The request could not be completed." instead of exception text; see `MgmtUser`/`MgmtLogs`. |
| [x] | 2.4.3 | Controller/database exception handling | Every `SQLite` method catches and routes through `logError`; no unhandled exception propagates to a view. |
| [ ] | 2.4.4 | Safe unprivileged fallback panel | Partially addressed: an unauthenticated/disabled session shows no nav buttons and an empty content area. No dedicated "safe panel" view yet for unhandled runtime exceptions (e.g. a global uncaught-exception handler) — still pending. |
| [x] | 2.5.1 | Admin-only log access and management | `MgmtLogs` only reachable via `Frame`'s `ROLE_ADMIN`-gated nav; CLEAR now functional. |
| [ ] | 2.5.2 | Timestamped validation-failure logs | `SQLite.addLogs` infra ready; not yet called from input-validation failure sites in A/B's forms (Login/Register/MgmtProduct). |
| [ ] | 2.5.3 | Timestamped authentication logs | Infra ready (`SQLite.addLogs`); Bundle A's login flow needs to call it on success/failure — not yet wired since Login.java is Bundle A's scope. |
| [x] | 2.5.4 | Unauthorized-access logs | `Authorization.check` logs every denied access as `ACCESS_DENIED`. |

Evidence (2026-10-28): Bundle C (centralized authorization, admin user management, audit logging, shared validation/error handling) implemented — `Controller/Authorization.java`, `Controller/Validator.java`, `Controller/PasswordUtil.java`, rewritten `Controller/SQLite.java`, role-gated `View/Frame.java`, and functional `View/MgmtUser.java`/`View/MgmtLogs.java`. Full detail and rationale in [security-documentation.md](security-documentation.md) and [ADR 0001](adr/0001-account-disable-state.md). Compiled successfully with `javac` (JDK 21) against all 20 sources; `ant` was unavailable in this environment so the full `ant -f app/build.xml clean init-db jar` + packaged-JAR run was not re-verified here — do that before relying on this as final evidence. The Swing UI launched and stayed open under a live `$DISPLAY` for a short smoke test with no exceptions, but interactive navigation/admin-action behavior was not manually clicked through.

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
- [ ] Agree on task dissemination/dibs and create implementation issues.
- [ ] Resolve implementation choices such as password length limits during the later design/ticketing session; the checklist requires bounds but does not specify their numeric values.
