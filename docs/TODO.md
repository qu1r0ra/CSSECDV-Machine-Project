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

## Integrated candidate and acceptance evidence (2026-11-19)

This section supersedes the pre-integration status snapshot above. Issue [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9) is integrated in the assigned #15 branch; this is the #17 evidence and delivery batch. Candidate code commit: ba2ad17f6825e3fb63ac457c2aae78aa30eded3e, based on integrated commit ff7f61e27c4fe6686f8a7992a8edef6bd9626b95. This exact commit passed the integrated A/B/UI regression target before the evidence was recorded. No PR or merge to main was created.

### Integrated candidate review repairs (2026-11-19)

The #15 candidate review findings were independently checked against the issue #15/#16/#9/#17 acceptance criteria, `docs/specifications.md`, and `docs/checklist.md`. The defects were present at review base `7ba394803f9f3ceac2d0884a33ec92dc9bab638e`. Source, tests, Ant smoke-test isolation, and the rebuilt package were committed as code revision `df2b8c9bcf9160fda72972f800368057af321d05`. The exact code revision passed the commands below on JDK 21.0.13 and Ant 1.10.18:

| Purpose | Exact command | Result |
| --- | --- | --- |
| Integrated A/B and Swing regression | `ant '-Dbuild.dir=build-candidate-review-15' '-Ddist.dir=dist-candidate-review-15' -f build.xml test-regression-final-17` | BUILD SUCCESSFUL; A passed 279 assertions, B provider checks passed, and AssertJ Swing passed on the real display. The UI suite now checks visible role home panels as well as navigation buttons. |
| Standalone package smoke against the checked-in distribution | `ant '-Dbuild.dir=build-candidate-review-15' '-Ddist.dir=dist-candidate-review-15' '-Ddistribution.dir=submission/distribution' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml test-distribution` | BUILD SUCCESSFUL; the packaged JAR ran the Swing workflows against `build-candidate-review-15/distribution-smoke/database.db`. This historical target used Ant's default checksum algorithm (MD5) to compare the checked-in distribution DB before and after; the later independent re-review below changed the check to explicit SHA-256. |
| Rebuild checked-in standalone package | `ant '-Dbuild.dir=build-candidate-review-15' '-Ddist.dir=submission/distribution' '-Ddistribution.database=submission/distribution/database.db' -f build.xml jar` | BUILD SUCCESSFUL; rebuilt `app/submission/distribution/CSSECDV.jar` from the reviewed source. |
| Patch whitespace check | `git diff --check` | Passed before the code commit. |

The review repairs bind audit-log searches to the single trusted application `AuthService` whose database path matches the audit provider; the caller-supplied `AccessControl` can no longer substitute a Role 5 session from another database. Tests cover a signed-in Admin from a separate database, denial when the target app has no Admin session, and successful target Admin review. Known-user empty and oversized password submissions keep generic feedback, record validation and authentication failure events without the secret, advance the consecutive-failure state, and disable the account on the fifth failure. A failed required role-change audit now rolls back the role update, revokes the Admin session, and invokes the configured safe-state listener. The B provider distinguishes required audit-write exceptions from SQLite constraint conflicts; a trigger-injected failure in the real logs table verifies mutation rollback and safe fallback. Real-provider gateway coverage confirms Manager all-user history. Swing checks each role's actual visible home panel.

Additional 2.4.3 evidence now exercises an unexpected account-table failure after login, verifies session revocation and safe-state notification, and checks that account administration returns denial/empty outcomes. Existing A/B failure tests cover provider errors, required-audit rollback, and business safe fallback. This narrows the evidence gap but does not prove a universal crash boundary around every controller and database method; checklist 2.4.3 remains unchecked.

### Independent re-review repairs (2026-11-19)

Code revision `e87494e39aa004101f1d313975f1c611bc557bec` repairs the independent #15 re-review findings while preserving the preceding candidate changes. Temporary-SQLite tests inject required audit write failures in role change, lock, unlock, delete, password change, and both successful and failed re-authentication. Each mutation rolls back, returns a generic failure, revokes the Admin session, and invokes its safe-state listener. Login tests cover malformed unknown-user credentials (both validation and authentication failure records, without the secret), malformed and valid-shaped attempts against a manually locked account (no counter or role transition), and the last-attempt timestamp/status shown after the next valid login. `SQLite.getLogs` continues to throw a generic controller failure rather than report false empty success; the `MgmtLogs` UI clears prior rows and displays its safe unavailable message. This controller/UI evidence does not establish a universal crash boundary; 2.4.3 remains unchecked.

The distribution target now compares its database with explicit SHA-256 and performs the before/after preservation check before reporting a nonzero Swing smoke-test result. An intentionally failing smoke run (missing external credentials file) exited 1 and preserved the delivered database SHA-256 exactly. This exercises a child-process failure path; it does not claim coverage for every Ant task-startup failure.

| Purpose | Exact command | Result |
| --- | --- | --- |
| Integrated provider and real-display Swing regression | `ant '-Dbuild.dir=build-review15-repair' '-Ddist.dir=dist-review15-repair' -f build.xml test-regression-final-17` | BUILD SUCCESSFUL on JDK 21.0.13 / Ant 1.10.18; A passed 311 assertions, B provider checks passed, and AssertJ Swing passed on the real display. |
| Rebuild tracked distribution | `ant '-Dbuild.dir=build-review15-repair' '-Ddist.dir=submission/distribution' '-Ddistribution.database=submission/distribution/database.db' -f build.xml jar` | BUILD SUCCESSFUL; packaged from source revision `e87494e`. |
| Standalone package smoke | `ant '-Dbuild.dir=build-review15-repair' '-Ddist.dir=dist-review15-repair' '-Ddistribution.dir=submission/distribution' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml test-distribution` | BUILD SUCCESSFUL; UI workflows ran from the packaged JAR against isolated `build-review15-repair/distribution-smoke/database.db`, preserving the checked-in database. |
| Expected failing package smoke | Same command with `-Ddemo.credentials=C:\Users\Quirora\AppData\Local\Temp\missing-issue15-demo-credentials.txt` | Ant exited 1 after the Swing child returned 1; the original distribution DB hash remained `4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7`. |
| Patch whitespace check | `git diff --check` | Passed before the code commit. |

Distribution SHA-256 after rebuild: `CSSECDV.jar` `a2a2e0048a33a81386cc800dd4d82166bf99322375c30f8931f583507e7672f4`; `database.db` `4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7`; `lib/sqlite-jdbc-3.23.1.jar` `d570af636a2be99e20be9510fb615aa819b2059857b2dd625aecbff774766331`.

The checked-in distribution currently contains exactly the executable JAR, `database.db`, and SQLite JDBC runtime. SHA-256 after rebuild:

| Runtime artifact | SHA-256 |
| --- | --- |
| CSSECDV.jar | a2a2e0048a33a81386cc800dd4d82166bf99322375c30f8931f583507e7672f4 |
| database.db | 4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7 |
| lib/sqlite-jdbc-3.23.1.jar | d570af636a2be99e20be9510fb615aa819b2059857b2dd625aecbff774766331 |

### Follow-up independent findings (2026-11-19)

Code revision `933b3c3eaa826bf438db241d74bb242ca0414f88` fixes the additional #15 findings. Valid-shaped login attempts against disabled or temporarily locked accounts now spend the same dummy PBKDF2 verification as unknown accounts; deterministic tests count verifier invocations. Blocked attempts update the prior-attempt timestamp/status without changing failed-attempt counters, role, or lock state; a successful login after unlock reports the failed attempt. Malformed usernames write `VALIDATION_FAILURE` and `AUTH_FAILURE` together in one transaction using actor `UNKNOWN` and no submitted username or password; a failure of either audit write rolls back both, returns a generic error, and revokes an existing session to the configured safe state. `UserAdminService` now revokes the Admin session and uses a generic failure if its standalone validation audit fails; tests cover invalid role, lock, delete, password policy, and confirmation paths. The prior historical result above is corrected to identify Ant's implicit checksum algorithm as MD5; the commands below use the explicit SHA-256 test already present in the distribution target. Checklist 2.4.3 remains unchecked.

Run from `app/` on JDK 21.0.13 and Ant 1.10.18:

| Purpose | Exact command | Result |
| --- | --- | --- |
| Integrated A/B and real-display Swing regression | `ant '-Dbuild.dir=build-rereview15' '-Ddist.dir=dist-rereview15' -f build.xml test-regression-final-17` | BUILD SUCCESSFUL at code revision `933b3c3eaa826bf438db241d74bb242ca0414f88`; A passed 334 assertions, B provider checks passed, and the real-display Swing suite passed. |
| Rebuild tracked standalone distribution | `ant '-Dbuild.dir=build-rereview15' '-Ddist.dir=submission/distribution' '-Ddistribution.database=submission/distribution/database.db' -f build.xml jar` | BUILD SUCCESSFUL; tracked executable JAR rebuilt from the tested source. |
| Standalone distribution Swing smoke | `ant '-Dbuild.dir=build-rereview15' '-Ddist.dir=dist-rereview15' '-Ddistribution.dir=submission/distribution' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml test-distribution` | BUILD SUCCESSFUL; packaged-JAR workflows used the disposable `build-rereview15/distribution-smoke/database.db`; the tracked database passed the target's before/after SHA-256 preservation check. |
| Patch whitespace check | `git diff --check` | Passed before commit. |

The tracked distribution SHA-256 after rebuild and standalone smoke is `CSSECDV.jar` `8d309be95fbb9668ea5c301689acf69241927cde107a83ec6970a29ebfa2a92c`, `database.db` `4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7`, and `lib/sqlite-jdbc-3.23.1.jar` `d570af636a2be99e20be9510fb615aa819b2059857b2dd625aecbff774766331`. The evidence update is docs-only after tested code revision `933b3c3eaa826bf438db241d74bb242ca0414f88`; 2.4.3 remains open absent universal controller/database failure-boundary evidence.

The earlier documentation/evidence update followed code revision `df2b8c9`; the independent re-review evidence above is bound to source revision `e87494e39aa004101f1d313975f1c611bc557bec`. No PR or merge was created. The completion checkout was not touched.

### Final reauthentication repair and package verification (2026-11-19)

Code revision `3cc248287f1df93c582cd84293c48140957dd3f9` binds malformed reauthentication to the trusted active session before audit attribution. For a valid actor, malformed reauthentication records `VALIDATION_FAILURE` and `AUTH_FAILURE` and increments the separate reauthentication-failure counter in one transaction; it does not increment the five-failed-login counter. Tests cover forged and stale actors, the transactional counter/audit result, and rollback with session revocation after an audit failure. The temporary-lock test exercises the actual Admin unlock service before checking the user's next successful login. A read-only spec review using gpt-6.1-terra at medium effort found no actionable gap in this diff. Checklist 2.4.3 remains open because these cases still do not establish a boundary around every controller and database method.

| Purpose | Exact command | Result |
| --- | --- | --- |
| Integrated A/B and Swing regression | `ant '-Dbuild.dir=build-final-round' '-Ddist.dir=dist-final-round' -f build.xml test-regression-final-17` | BUILD SUCCESSFUL at code revision `3cc248287f1df93c582cd84293c48140957dd3f9`; A passed 347 assertions, B provider checks passed, and the real-display Swing suite passed. |
| Rebuild executable JAR | `ant '-Dbuild.dir=build-package' '-Ddist.dir=dist-package' -f build.xml jar` | BUILD SUCCESSFUL; rebuilt from `3cc2482` and copied the JAR into the tracked standalone distribution without replacing the prepared demo database. |
| Standalone package Swing smoke | `ant '-Dbuild.dir=build-package' '-Ddist.dir=dist-package' '-Ddistribution.dir=submission/distribution' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml test-distribution` | BUILD SUCCESSFUL; packaged UI workflows used an isolated database copy, and the tracked demo database remained unchanged. |
| Patch whitespace check | `git diff --check` | Passed after the code changes. |

The distribution SHA-256 at that earlier revision was `CSSECDV.jar` `d466e5f70214a672f05764783d9754cbacbdd109d0b8a2cb9daf4bbb140ac33a`, `database.db` `4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7`, and `lib/sqlite-jdbc-3.23.1.jar` `d570af636a2be99e20be9510fb615aa819b2059857b2dd625aecbff774766331`. The distribution target passed its before/after SHA-256 preservation check; an independent post-run hash matched the recorded database hash.

The first issue #17 acceptance batch passed on Windows with JDK 21.0.13 and Ant 1.10.18. The Swing target ran non-headless on the real desktop display; AssertJ Swing's AWT Robot drove the actual Swing controls. Its UI database was temporary and isolated. The final reauthentication repair and regression rerun are recorded above; the earlier assertion count below is historical.

### Earlier commands and results (initial #17 acceptance batch)

Run from the repository app directory unless noted.

| Purpose | Exact command | Result |
| --- | --- | --- |
| Rerun requested Swing UI acceptance suite | ant '-Dbuild.dir=build-final-17' '-Ddist.dir=dist-final-17' -f build.xml test-ui | BUILD SUCCESSFUL; registration/login, masked controls, all four enabled-role routes, denied direct Client product mutation, purchase confirmation, exact five-attempt lockout, and Admin audit search/show-all passed. |
| Integrated A/B/UI regression | ant '-Dbuild.dir=build-final-17' '-Ddist.dir=dist-final-17' -f build.xml test-regression-final-17 | BUILD SUCCESSFUL; A passed 260 assertions against temporary SQLite, B provider checks passed, and real-display Swing acceptance passed. |
| Prepare demo DB and operator-only credentials | ant '-Dbuild.dir=build-final-17' '-Ddist.dir=dist-final-17' '-Ddemo.database=fixtures/demo.db' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml prepare-demo-database | BUILD SUCCESSFUL; fresh database created with four hashed role accounts and two products. The generated plaintext passwords are in the external operator-only file, never in the database or tracked source. |
| Package against prepared demo data | ant '-Dbuild.dir=build-final-17' '-Ddist.dir=dist-final-17' '-Ddistribution.database=fixtures/demo.db' -f build.xml jar | BUILD SUCCESSFUL; executable JAR, manifest, JDBC runtime, and prepared database copied into the standalone distribution. |
| Test standalone distribution from a separate directory | ant '-Dbuild.dir=build-final-17' '-Ddist.dir=dist-final-17' '-Ddistribution.dir=C:\Users\Quirora\AppData\Local\Temp\CSSECDV-final17-smoke-postcommit-20261119' '-Ddemo.credentials=C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt' -f build.xml test-distribution | BUILD SUCCESSFUL; same Swing workflows loaded through the packaged JAR and ran against a disposable copy of database.db, leaving the delivered database unchanged. |
| Direct executable-JAR launch | From the separate distribution directory: java -jar .\CSSECDV.jar | Process opened the CSSECDV - SECURITY Svcs window and closed normally. |

Test-only dependencies are pinned in app/test-lib: AssertJ Swing 3.17.1, AssertJ Core 3.17.2, and FEST Util 1.2.5. These were checked against official AssertJ Swing repository/Maven metadata and are used only by test-ui, test-regression-final-17, and test-distribution. They are absent from the runtime JAR and distribution. The Swing target cannot run headless because its AWT Robot sends native OS-level input.

### Prepared distribution inspection

app/submission/distribution/ is the standalone deliverable. It contains exactly CSSECDV.jar, database.db, and lib/sqlite-jdbc-3.23.1.jar. The JAR manifest names Controller.Main and the relative SQLite JDBC runtime. The prepared SQLite DB contains one account each at Roles 2, 3, 4, and 5; all four stored credentials use the production PBKDF2-SHA256 format at 600,000 iterations. It contains two catalog products, zero legacy history rows, zero purchase test rows, and two setup audit rows. No plaintext credential or test-only account is stored in that DB. The delivered DB's SHA-256 was inspected before and after copy-based testing and remained 4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7.

| Runtime artifact | SHA-256 |
| --- | --- |
| CSSECDV.jar | 097687ed5392c911c3df8a0685c9d038ed0bf57362a34ccd1301f9f692f7e470 |
| database.db | 4105797b0c4cd289b3a4b9b5645d8618a4540b79822c431a3aa4f41537db9af7 |
| lib/sqlite-jdbc-3.23.1.jar | d570af636a2be99e20be9510fb615aa819b2059857b2dd625aecbff774766331 |

Operator demo passwords are generated for the prepared database and held only in C:\Users\Quirora\AppData\Local\CSSECDV-Machine-Project\demo-credentials-issue-17.txt, outside the checkout with an owner/system/admin-only ACL. They are not copied into project resources or the tracked package. The seeder refuses to overwrite an existing database. The generated app/fixtures/demo.db staging copy is not part of the final tracked artifacts; the distribution copy is the prepared DB.

### Checklist readiness against this candidate

Final checklist follow-up at source revision `d00a50c`: audit search rejects input outside its documented allowlist and records validation failures. Unexpected startup failures produce a generic error and leave no active session; an unexpected Swing event-handler failure revokes the session, returns to Login, and displays a generic error. The integrated `test-regression-final-17` and `jar` targets passed using isolated `build-tdd`/`dist-tdd` outputs (Bundle A: 348 assertions; Bundle B and Swing passed). The packaged distribution smoke test also passed using a disposable database copy and confirmed that the delivered database was unchanged. The JAR from that earlier run has since been replaced; see the current distribution table above and the subsequent controller boundary inventory.

All 29 scored items have implementation and representative behavior evidence. The controller and database boundary inventory below records the scope and limits of the 2.4.3 assessment; individual failure injection is not exhaustive.

| Ready | Checklist ID | Candidate evidence |
| --- | --- | --- |
| [x] | 1.1.1 | Prepared Role 5 demo Admin successfully signs in in the distribution smoke suite. |
| [x] | 1.1.2 | Prepared Role 3 Staff and Role 4 Manager accounts successfully sign in and reach their own homes. |
| [x] | 1.1.3 | Prepared Role 2 Client successfully signs in; Swing registration also creates a Client account. |
| [x] | 2.1.1 | Provider tests cover protected operations and denial; UI routes and role gates are checked through the integrated suite. |
| [x] | 2.1.2 | Auth, business, and required-audit provider failures fail closed; stale/disabled session returns to the unprivileged Login panel. |
| [x] | 2.1.3 | Demo DB inspection found four PBKDF2-SHA256 hashes and no plaintext password rows; A tests cover hashing and legacy migration. |
| [x] | 2.1.4 | A provider and Swing tests verify the generic invalid-credential response. |
| [x] | 2.1.5 | A provider tests cover required character classes and rejection of weak passwords. |
| [x] | 2.1.6 | A provider tests cover the 8–64 character boundary. |
| [x] | 2.1.7 | Swing verifies masked login, registration, and confirmation password fields. |
| [x] | 2.1.8 | A provider and Swing tests verify the account stays enabled through four failures and becomes Role 1 on the fifth; an oversized known-user password is also verified as the fifth failure. Success resets the sequence. |
| [x] | 2.1.9 | A provider and Swing tests verify the prior attempt timestamp and status are reported after successful login. |
| [x] | 2.1.10 | A/B providers test current-password reauthentication and operation binding; Swing tests the purchase confirmation step. |
| [x] | 2.2.1 | Central AccessControl checks role homes, user administration, and audit review; business providers enforce operation-level authorization. |
| [x] | 2.2.2 | Provider role matrices and Swing route/control visibility checks cover Client, Staff, Manager, and Admin behavior. |
| [x] | 2.2.3 | Provider/storage failures and unauthorized direct calls return denial or safe failure without granting privileges. |
| [x] | 2.2.4 | Swing verifies each enabled role's actual visible home panel and hides the other role panels; disabled/stale actor is returned to Login. |
| [x] | 2.3.1 | A/B validation tests reject malformed and over-limit inputs without sanitizing or partially mutating state. |
| [x] | 2.3.2 | A/B tests cover role, quantity, stock, and exact price range/boundary handling. |
| [x] | 2.3.3 | A/B tests cover account, catalog, quantity, and search length boundaries. |
| [x] | 2.3.4 | Runtime data queries use parameterized providers; the former unsafe compatibility CRUD surface is retired, and audit filters are parameterized. |
| [x] | 2.4.1 | Production sources no longer print stack traces or provider details to end users; failure paths return safe outcomes. |
| [x] | 2.4.2 | UI receives fixed, user-friendly service messages; provider details are not shown. |
| [x] | 2.4.3 | Startup and the shared Swing event boundary contain recoverable application-path failures; operation/transaction boundaries handle storage errors and required audit rollback. The inventory below maps controller and database call paths; representative startup, SQLite, provider, runtime and assertion failures are tested. Fatal JVM failures and exhaustive per-method injection are outside this evidence. |
| [x] | 2.4.4 | B provider failure paths invoke the safe fallback; Swing verifies a revoked/disabled session reaches the Login panel. |
| [x] | 2.5.1 | Role checks restrict account administration; audit search uses the trusted app session bound to the same database and denies a foreign database Admin; the UI allows Admin search/show-all and exposes no permanent audit-delete operation. |
| [x] | 2.5.2 | A/B provider tests verify timestamped validation events and persistence. |
| [x] | 2.5.3 | A provider tests verify timestamped successful and failed login and reauthentication events, including malformed reauthentication without logging submitted passwords. |
| [x] | 2.5.4 | A/B provider tests verify unauthorized resource attempts are denied and recorded. |

### Required functionality and delivery status

- [x] Security documentation uses vulnerability / impact / exact implemented control rows and reconciles #15/#16 audit-retention and fail-closed rules.
- [x] Access-control matrix covers all listed resources/actions and the six assignment states (Unregistered, Disabled, Client, Staff, Manager, Admin).
- [x] Demo cases cover Authentication, Authorization, Data Validation, Error Handling, and Logging.
- [x] Isolated prepared demo database, operator-only credential delivery, executable JAR, runtime JDBC dependency, and standalone README are present.
- [x] A/B actual-provider regression and real-display Swing suite pass.
- [x] Copy-based standalone distribution UI suite and direct JAR launch pass; delivery DB remains unchanged.
- [x] git diff --check passes.
- [x] NetBeans 32 smoke launch (2026-11-19): Apache NetBeans 32 was installed and opened on a disposable candidate source snapshot at `2bdef76`; the application window launched and responded. This confirms a basic IDE open/build/run path, not complete security acceptance. The final source revision separately passed the integrated and packaged UI tests recorded above.

Final boundary follow-up: the shared Swing failure boundary is installed before the window is shown and handles recoverable `RuntimeException` and `AssertionError` paths by revoking the session and returning to Login. Admin list storage errors are signaled as unavailable and outer mutation exceptions revoke the session. Recovery attempts to hide and dispose the frame if safe-panel navigation fails; disposal failures are contained. `ant -Dbuild.dir=build-boundary -Ddist.dir=dist-boundary -f app/build.xml test-regression-final-17 jar` passed (Bundle A 348 assertions, Bundle B, and real-display Swing). The packaged distribution smoke used a disposable database copy. A proposed targeted Admin-list SQLite fault fixture failed during identity refresh instead of reaching the list query, so no isolated list-query fault test is claimed; that handling was assessed by source review.

### Controller and database failure boundaries (#9)

This inventory covers the shipped desktop application call paths, rather than promising recovery from a fatal JVM or operating-system failure. Application event handlers run on Swing's event-dispatch thread; no application worker threads were found in `app/src`. Helpers may propagate errors to their transaction or UI boundary so a failed audit is not mistaken for success.

| Entry point / storage calls | Failure boundary and observable behavior |
| --- | --- |
| Launch and frame initialization | `Main.init` guards startup; recoverable failures leave no active frame or session. The event boundary is installed before the frame is shown. |
| Swing actions and navigation | The shared event queue catches recoverable unhandled failures, revokes the session, and routes to Login with a generic message; if the UI cannot recover it disposes the frame. |
| Authentication, account store, session refresh, and access checks | Authentication returns denial or generic failure; refresh revokes invalid sessions. Transaction owners roll back on errors, including required audit writes. |
| Admin account list and mutations | Admin methods contain ordinary failures; mutations roll back and revoke the session for unrecoverable failures. A failed list query is signaled to the user view, which clears rows and displays a generic error. |
| Catalog, purchases, and history | Bundle B service boundaries return safe results or invoke the safe-state callback. Mutation transactions roll back stock, history, and audit together. |
| Audit initialization, writes, and search | Storage errors propagate as generic failures to the startup, transaction, authentication, or log-view boundary; log review clears stale rows and displays an unavailable message. |
| Validators, password helpers, immutable result/accessor methods | These are synchronous helpers invoked by the protected controller or event call paths above; unexpected errors reach those enclosing boundaries. `DemoAccountSeeder` is a separate operator tool, not an application controller entry point. |

The automated regression exercises representative real SQLite and audit failures, transactional rollback, startup failure, and both runtime and assertion failures dispatched through Swing. It does not inject every fault into every helper or establish recovery from fatal JVM conditions.

### Reconciliation and remaining gap

The tested artifacts satisfy the issue #17 delivery scope against the assignment materials, rubric/checklist, #15, and settled Bundle B. The settled #15/#16 constraints are preserved: there is no permanent audit-delete action, required audit-write failures fail closed, the test and distribution-smoke databases are isolated, and recorded evidence reflects executed commands. No test passwords or demo plaintext credentials are tracked. The controller and database boundary inventory above records how the application paths meet 2.4.3 and identifies the limits of the representative failure tests. NetBeans smoke testing established the IDE open/build/run path on a disposable earlier source snapshot; the final code's behavior was verified through the recorded Ant and packaged UI suites.

## Scheduling and later decisions


The October 20 class announcement identified **November 20, 2026** as the first demo date, with November 24 or 27 mentioned for later sessions. Our assigned slot and a common final code-submission deadline are not established by these handouts. Treat the earliest date as a preparation target until the assigned schedule is confirmed. The same announcement said no PowerPoint was required.

- [ ] Confirm our assigned demo slot and final submission deadline when announced.
- [x] Agree on Bundle A/B/C ownership and scope; see [GitHub Issue #4](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4). Create implementation issues with shared interfaces and dependencies before parallel work.
- [x] Settle and publish [Bundle B specification #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6), including the confirmed testing seam and A/C dependencies. Publication evidence (2026-11-02): the live body matches the settled specification, all seven template sections and 24 user stories are present, and `ready-for-agent` is applied. This is planning evidence, not implementation compliance.
- [x] Approve and publish the [B-only implementation plan](bundle-b-spec.md): [catalog #7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) precedes [purchases/history #8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8). Publication evidence (2026-11-02): verified exact live bodies with 22 acceptance criteria (including the readiness audit's history-search validation/ownership clarification), `ready-for-agent` labels, Christian's self-assignment, native sub-issues of #6, #8 blocked by #7 and the matching approved map in #6. Implemented and landed in [PR #10](https://github.com/qu1r0ra/CSSECDV-Machine-Project/pull/10) as `47138fe` (2026-11-02); [#7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) and [#8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8) are closed. A/C integration remains the acceptance gate in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).
- [x] Password length bounds: 8 minimum and 64 maximum characters, following common guidance (NIST SP 800-63B). The checklist requires bounds but does not give values. `InputValidator` on current `main` enforces the bounds; behavior evidence still needs to be run and recorded against the integrated revision.
- [x] The historical Bundle C reconciliation note is superseded by the integrated A/B/C providers and evidence recorded above; remaining follow-up is tracked in [#9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).
