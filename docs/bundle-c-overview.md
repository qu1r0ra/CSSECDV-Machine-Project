# Bundle C overview: administration and shared security

Bundle C is the security layer that the rest of the application uses. Bundles A (authentication) and B (products, purchases, history) own their features. Bundle C provides the components those features call to ask "may this user do this?", "is this input acceptable?", "record this event", and "something failed, go to a safe state". It also owns the Admin features (user management and log review) and the wiring and packaging that combine everything into one application.

Requirements: [checklist](checklist.md) §2.2–2.5. Per-control evidence: [security documentation](security-documentation.md). Per-role permissions: [access control matrix](access-control-matrix.md).

## How the pieces fit

```text
            Swing views (Frame, role homes, MgmtUser, MgmtLogs, MgmtProduct, ...)
                                   │
        ┌──────────────────────────┼─────────────────────────────┐
        ▼                          ▼                             ▼
  AuthService (A)          UserAdminService (C)          BundleBService (B)
        │                          │                             │
        │                          ▼                             ▼
        │                  AccessControl (C) ◄──── AppSecurityGateway (C adapter)
        │                          │
        ├──────────────► InputValidator (C)   ◄── used by A, C (and mirrored rules in B)
        └──────────────► SecurityAudit / SqliteSecurityAudit (C) ◄── every bundle logs here
                                   │
                              SQLite `logs` table
```

`Main.initialize` (`app/src/Controller/Main.java:459`) builds all of these from one database path, binds them together, and installs the global UI error boundary.

## 1. Centralized authorization: `AccessControl`

File: `app/src/Controller/AccessControl.java`

Checklist 2.2.1 requires a single component that checks access. This class is that component.

- **One role table.** The `Feature` enum lists every protected feature and the roles allowed to use it. For example, `MANAGE_USERS(5)`, `MANAGE_PRODUCTS(3, 4)`, `PURCHASE(2)`, and `READ_ALL_HISTORY(4)`. Role 1 (Disabled) and unknown roles never pass (`roleMayUse`, line 32).
- **Role → home mapping.** `homeFor(role)` returns the single home panel for a role (5 → Admin, 4 → Manager, 3 → Staff, 2 → Client, anything else → none). This supports checklist 2.2.4.
- **`require(feature)`** is the main entry point. It does not trust a cached role. It asks `AuthService.currentActor()`, which re-reads the account from the database each time and revokes the session if the account was disabled, deleted, or had its role changed (`AuthService.java:213`). When access is denied, it records an `ACCESS_DENIED` audit event (checklist 2.5.4) and returns `null`.
- **Fails closed.** Any exception inside the check produces `null` (deny), as checklist 2.2.3 requires.

### Bridging to Bundle B: `AppSecurityGateway`

File: `app/src/Controller/AppSecurityGateway.java`

Bundle B was built against its own `BundleBService.SecurityGateway` interface. This adapter implements that interface using C's components:

- `authorize(...)` maps each B `Operation` to an `AccessControl.Feature` (`featureFor`, line 37). It confirms that the caller is still the same session (`sameSession`) and then consults the shared role table.
- `verifyCurrentPassword(...)` delegates to A's re-authentication.
- `onUnrecoverableFailure()` logs the user out and returns the UI to the safe panel.

B never contains a separate copy of the role rules. All of them come from `AccessControl`.

## 2. Role navigation: `View/Frame.java`

- After login, `enterHomeForCurrentRole()` (line 464) hides every role button and then shows only the button and panel for the user's own role. It runs `main.access.check(home)` before showing anything.
- Every home button click goes through `showHomePanel(feature, ...)` (line 480), which re-checks access. An unauthorized click shows a warning. If the session has gone, it calls `showSafeUnprivilegedState()`.
- `showSafeUnprivilegedState()` (line 511) logs out, resets every role panel to its blank home card, and shows the Login screen. Logout, revoked sessions, and unrecoverable errors all use this method (checklist 2.4.4).
- `AdminHome` hides the Products and History buttons because the Admin has no product or purchase privileges in the matrix.

## 3. Admin user management: `UserAdminService` + `MgmtUser`

Files: `app/src/Controller/UserAdminService.java`, `app/src/View/MgmtUser.java`

Every operation follows the same pattern:

1. **Authorize.** `access.require(MANAGE_USERS)` must return a Role 5 actor. Otherwise the operation returns a generic "not available" message.
2. **Validate strictly.** IDs must be positive. Roles must match exactly one digit from 1 to 5 (`InputValidator.parseRole`). New passwords must pass the creation policy. An Admin cannot change their own role, lock their own account, or delete their own account. Rejected input is logged as `VALIDATION_FAILURE`.
3. **Re-authenticate** for sensitive actions (role change, delete, password change). The Admin must re-enter their own password (`auth.verifyCurrentPassword`). Then `stillSame(admin)` confirms the session didn't change during the prompt.
4. **Mutate and audit in one transaction.** The change and its `USER_ADMIN` log row commit together. If the audit write fails, the change rolls back and the Admin session ends (`failSafeSession`).

| Operation | Method | Re-auth? |
| --- | --- | --- |
| List users (no password hashes exposed) | `listUsers()` | No |
| Change role, which includes **re-enabling** a Role 1 account by assigning it role 2–5 | `changeRole(...)` | Yes |
| Lock / unlock | `setLocked(...)` | No |
| Delete account | `deleteUser(...)` | Yes |
| Set another user's password | `changePassword(...)` | Yes |

Re-enabling has no separate method. An account locked out after 5 failed logins (Role 1) can only become usable again when an Admin assigns it a real role through `changeRole`.

## 4. Audit logging: `SecurityAudit` + `SqliteSecurityAudit` + `MgmtLogs`

Files: `app/src/Controller/SecurityAudit.java`, `app/src/Controller/SqliteSecurityAudit.java`, `app/src/Controller/SQLite.java`, `app/src/View/MgmtLogs.java`

- **`SecurityAudit`** is a small interface that every bundle writes events to. It has two forms:
  - `record(event, user, detail)` writes the event in its own transaction.
  - `record(connection, event, user, detail)` writes inside the caller's transaction, so a business change and its log row succeed or fail together. A, B, and C all use this form for mutations.
- **Event types:** `AUTH_SUCCESS`, `AUTH_FAILURE`, `ACCOUNT_DISABLED`, `VALIDATION_FAILURE`, `ACCESS_DENIED`, `REGISTRATION`, `USER_ADMIN`, plus B's product and purchase events.
- **`SqliteSecurityAudit`** stores events in the `logs` table with a prepared `INSERT` and a UTC ISO-8601 timestamp. It also bounds field lengths and restricts event names to `[A-Za-z0-9_]`. Passwords are never logged.
- **Append-only.** No operation in the application deletes log rows. `initialize()` is additive (`CREATE TABLE IF NOT EXISTS`).
- **Admin-only review (checklist 2.5.1).** `search(...)` ignores any `AccessControl` that the caller passes in. It uses its own `trustedAuth`, which is bound once at startup (`bindTrustedAuth`) and must point at the same database. It then requires `VIEW_LOGS` (Role 5). Search text is validated before use (rejected text is logged as a validation failure) and passed only as query parameters. Results are read-only and newest first.
- **`SQLite.java`** used to contain all of the legacy starter CRUD. That code has been removed. The class now only forwards `getLogs` to the audit provider, so the old unsafe queries are no longer in the codebase.
- **`MgmtLogs`** shows the search box and a "show all" view. It has no clear or delete button.

## 5. Shared validation: `InputValidator`

File: `app/src/Controller/InputValidator.java`

Each check either accepts the input exactly as given or rejects it. The validator never trims or cleans input (checklist 2.3.1).

- Username: 3–20 ASCII letters and digits.
- Password policy for account creation: 8–64 printable ASCII characters, with at least one uppercase letter, one lowercase letter, one digit, and one symbol. At login, only the length and character set are checked.
- Role: exactly `"1"`–`"5"` (`parseRole`) and range-checked (`isValidRole`). This covers checklist 2.3.2.
- `sameSecret`: compares the password and its confirmation in constant time.

A uses these checks for registration and login, and C uses them for user administration. B applies the same reject-only rules to product, price, quantity, and search fields inside `BundleBService`. All database access in every bundle uses `PreparedStatement` (checklist 2.3.4).

## 6. Shared error handling (checklist 2.4)

- **Generic messages only.** Services return fixed strings such as "The action could not be completed." and "Please check the entered information." SQL and Java exception text never reaches the user.
- **Guarded startup** (`Main.init`). If initialization fails, the app shows a generic dialog. If the audit, auth, or Bundle B storage fails to initialize, the app still starts, but the affected actions are refused instead of running.
- **Global UI boundary** (`Main.java:481`). A custom `EventQueue` catches any exception thrown from a Swing event. It ends the session, returns to Login, and shows "Please sign in again." This means one failing button can't crash the app or leave a privileged screen open. Fatal JVM errors (`VirtualMachineError`) are re-thrown, not hidden.
- **Session-revoked listener.** When `AuthService` ends a session by itself (the account was disabled or changed, or a storage error occurred), the listener sends the UI to the safe panel.

## 7. Integration and packaging

- **Wiring:** `Main.initialize` creates the audit provider, `AuthService`, `AccessControl`, `UserAdminService`, and `BundleBService` (using `AppSecurityGateway`) from one database path. It then binds the audit provider to the trusted `AuthService`.
- **Demo accounts:** `DemoAccountSeeder` (Ant target `prepare-demo-database`) creates one Admin, one Manager, one Staff, and one Client account, with hashed passwords that meet the policy. It refuses to overwrite an existing database. It writes the plaintext credentials to a file you choose outside the repository. This satisfies checklist §1.1.
- **Tests (Ant):** `test-bundle-a`, `test-bundle-b`, `test-ui` (AssertJ Swing on a real display), `test-regression-final-17` (all three), and `test-distribution` (runs the packaged JAR against a copy of the demo database).
- **Deliverable:** `app/submission/distribution/` contains `CSSECDV.jar`, `lib/sqlite-jdbc-3.23.1.jar`, and the prepared `database.db`. Test libraries are not included. See the [submission README](../app/submission/README.md).

## Quick map: checklist item → Bundle C code

| Checklist | Where |
| --- | --- |
| 2.2.1 single authorization component | `AccessControl.require` |
| 2.2.2 UI matches roles | `Frame.enterHomeForCurrentRole`, `AdminHome` hidden buttons |
| 2.2.3 fail-secure authorization | `AccessControl.require` catch → null; `AppSecurityGateway.authorize` catch → false |
| 2.2.4 role-specific home | `AccessControl.homeFor` + `Frame` |
| 2.3.1–2.3.3 strict validation | `InputValidator`, `UserAdminService`, audit search filters |
| 2.3.4 prepared statements | `SqliteSecurityAudit`, `AccountStore`, `BundleBService` |
| 2.4.1–2.4.4 safe errors | `Main` event boundary, generic service results, `showSafeUnprivilegedState` |
| 2.5.1 Admin-only logs | `SqliteSecurityAudit.search` (`VIEW_LOGS`), `MgmtLogs` |
| 2.5.2–2.5.4 event logging | `SecurityAudit.record` calls in A/B/C; `ACCESS_DENIED` in `AccessControl` |
