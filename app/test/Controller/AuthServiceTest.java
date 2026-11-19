package Controller;

import Model.Logs;
import View.MgmtLogs;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** Behavior checks for authentication, authorization and data validation against real temporary SQLite databases. */
public final class AuthServiceTest {
    private static final String GOOD = "Str0ng!Pass";
    private static int checks;

    private AuthServiceTest() { }

    public static void main(String[] args) throws Exception {
        validatorRejectsEverythingOutsideTheWhitelists();
        hasherIsSaltedOneWayAndRejectsMalformedValues();
        legacyPlaintextIsHashedOnInitialize();
        registrationEnforcesPolicyAndAssignsClientRole();
        duplicateAndCaseVariantUsernamesGiveTheGenericFailure();
        loginFailuresAreIndistinguishable();
        nonActiveAccountsSpendDummyPasswordWork();
        malformedAndLockedLoginAttemptsAreHandledConsistently();
        lockoutHappensOnTheFifthConsecutiveFailureOnly();
        disabledAccountCannotLoginEvenWithCorrectPassword();
        lastLoginAttemptIsReportedOnSuccess();
        sessionIsRevalidatedAgainstTheDatabase();
        roleTableMatchesTheAccessControlMatrix();
        injectionShapedInputIsRejectedNotSanitized();
        reauthenticationFailuresAreSeparateFromLoginLockout();
        adminOperationsAreAuthorizedValidatedAndReauthenticated();
        bundleBWorksThroughTheRealGateway();
        authenticationRollsBackWhenAuditPersistenceFails();
        registrationRollsBackWhenAuditPersistenceFails();
        userAdministrationRollsBackWhenAuditPersistenceFails();
        userAdministrationValidationAuditFailureRevokesSession();
        reauthenticationAuditFailureRevokesSession();
        auditProviderPersistsFiltersAndRestrictsLogReview();
        unexpectedDatabaseFailuresRevokeSessionsAndDenyControllers();
        failsClosedWhenStorageIsUnavailable();
        System.out.println("Bundle A behavior checks passed (" + checks + " assertions).");
    }

    // ------------------------------------------------------------------ validation / hashing

    private static void validatorRejectsEverythingOutsideTheWhitelists() {
        check(InputValidator.isValidUsername("client1"), "alphanumeric username accepted");
        check(InputValidator.isValidUsername("abc") && InputValidator.isValidUsername(repeat('a', 20)), "username bounds accepted");
        String[] bad = {null, "", "ab", repeat('a', 21), "bad name", "bad_name", "bad-name", " admin", "admin ", "adm\u00efn", "a'b", "a;b", "<b>x</b>", "x\n"};
        for (String name : bad) { check(!InputValidator.isValidUsername(name), "username rejected: " + name); }
        check(InputValidator.passwordPolicyViolation(GOOD.toCharArray()) == null, "compliant password accepted");
        String[] weak = {"", "Sh0rt!a", "nouppercase1!", "NOLOWERCASE1!", "NoDigitsHere!", "NoSymbols123A", "Has Space1!", "Unic\u00f6de1!Aa", repeat('A', 33) + repeat('a', 30) + "1!1"};
        for (String pw : weak) { check(InputValidator.passwordPolicyViolation(pw.toCharArray()) != null, "password rejected: " + pw.length() + " chars"); }
        check(InputValidator.passwordPolicyViolation(("Aa1!" + repeat('x', 60)).toCharArray()) == null, "64-char password accepted");
        check(InputValidator.passwordPolicyViolation(("Aa1!" + repeat('x', 61)).toCharArray()) != null, "65-char password rejected");
        check(InputValidator.parseRole("1") == 1 && InputValidator.parseRole("5") == 5, "roles 1 and 5 parse");
        for (String r : new String[] {"0", "6", "-1", "", "12", "a", " 2", "2 ", null}) { check(InputValidator.parseRole(r) == null, "role rejected: " + r); }
        check(!InputValidator.sameSecret("abc".toCharArray(), "abd".toCharArray()) && InputValidator.sameSecret("abc".toCharArray(), "abc".toCharArray()), "secret comparison");
        check(!InputValidator.sameSecret("abc".toCharArray(), new char[0]), "empty confirmation never matches");
    }

    private static void hasherIsSaltedOneWayAndRejectsMalformedValues() {
        String first = PasswordHasher.hash(GOOD.toCharArray());
        String second = PasswordHasher.hash(GOOD.toCharArray());
        check(!first.equals(second), "same password hashes differently (unique salt)");
        check(!first.contains(GOOD) && first.startsWith("pbkdf2-sha256$600000$"), "hash is not the password and records its work factor");
        check(PasswordHasher.verify(GOOD.toCharArray(), first) && PasswordHasher.verify(GOOD.toCharArray(), second), "correct password verifies");
        check(!PasswordHasher.verify("Wrong!Pass1".toCharArray(), first), "wrong password fails");
        String[] malformed = {null, "", "plain", "pbkdf2-sha256$x$a$b", "pbkdf2-sha256$0$AAAA$AAAA", "pbkdf2-sha256$999999999$AAAA$AAAA", "pbkdf2-sha256$1$AA$AA", "md5$1$a$b"};
        for (String m : malformed) { check(!PasswordHasher.verify(GOOD.toCharArray(), m), "malformed stored value never matches"); }
        check(PasswordHasher.isHashFormat(first) && !PasswordHasher.isHashFormat("qwerty1234"), "hash format detection");
    }

    // ------------------------------------------------------------------ authentication

    private static void legacyPlaintextIsHashedOnInitialize() throws Exception {
        Path db = tempDb();
        try {
            try (Connection c = open(db); PreparedStatement s = c.prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, role INTEGER DEFAULT 2, locked INTEGER DEFAULT 0)")) { s.execute(); }
            execute(db, "INSERT INTO users(username,password,role) VALUES(?,?,?)", "admin", "qwerty1234", 5);
            AuthService auth = new AuthService(db, SecurityAudit.NONE);
            auth.initialize();
            String stored = scalar(db, "SELECT password FROM users WHERE username = ?", "admin");
            check(PasswordHasher.isHashFormat(stored) && !stored.contains("qwerty1234"), "legacy plaintext replaced by a salted hash");
            check(auth.login("admin", "qwerty1234".toCharArray()).isSuccess(), "migrated account still logs in with its original password");
            auth.initialize();
            check(stored.equals(scalar(db, "SELECT password FROM users WHERE username = ?", "admin")), "initialize is idempotent");
        } finally { Files.deleteIfExists(db); }
    }

    private static void registrationEnforcesPolicyAndAssignsClientRole() throws Exception {
        Fixture f = new Fixture();
        try {
            AuthService.RegistrationResult ok = f.auth.register("newuser", GOOD.toCharArray(), GOOD.toCharArray());
            check(ok.isSuccess(), "valid registration succeeds");
            check("2".equals(scalar(f.db, "SELECT role FROM users WHERE username = ?", "newuser")), "registered account gets the Client role");
            String stored = scalar(f.db, "SELECT password FROM users WHERE username = ?", "newuser");
            check(PasswordHasher.isHashFormat(stored) && !stored.contains(GOOD), "registered password is stored only as a hash");
            check(!f.auth.register("bad name", GOOD.toCharArray(), GOOD.toCharArray()).isSuccess(), "invalid username rejected");
            check(!f.auth.register("weakone", "password".toCharArray(), "password".toCharArray()).isSuccess(), "weak password rejected");
            check(!f.auth.register("mismatch", GOOD.toCharArray(), "Other!Pass1".toCharArray()).isSuccess(), "mismatched confirmation rejected");
            check(count(f.db, "SELECT COUNT(*) FROM users WHERE username IN ('weakone','mismatch')") == 0, "rejected registrations create nothing");
            char[] pw = GOOD.toCharArray();
            f.auth.register("clearme1", pw, GOOD.toCharArray());
            check(allZero(pw), "password array is cleared after use");
        } finally { f.close(); }
    }

    private static void duplicateAndCaseVariantUsernamesGiveTheGenericFailure() throws Exception {
        Fixture f = new Fixture();
        try {
            AuthService.RegistrationResult dup = f.auth.register("client1", GOOD.toCharArray(), GOOD.toCharArray());
            AuthService.RegistrationResult variant = f.auth.register("CLIENT1", GOOD.toCharArray(), GOOD.toCharArray());
            check(!dup.isSuccess() && !variant.isSuccess(), "duplicate and case-variant usernames are refused");
            check(dup.getMessage().equals(AuthService.REGISTRATION_FAILURE) && variant.getMessage().equals(dup.getMessage()), "duplicate failure message is generic");
        } finally { f.close(); }
    }

    private static void loginFailuresAreIndistinguishable() throws Exception {
        Fixture f = new Fixture();
        try {
            f.seed("disabled1", 1, GOOD);
            String[] messages = {
                f.auth.login("nosuchuser", GOOD.toCharArray()).getMessage(),
                f.auth.login("client1", "Wrong!Pass1".toCharArray()).getMessage(),
                f.auth.login("disabled1", GOOD.toCharArray()).getMessage(),
                f.auth.login("bad name!", GOOD.toCharArray()).getMessage(),
                f.auth.login("client1", repeat('x', 200).toCharArray()).getMessage(),
                f.auth.login("client1", new char[0]).getMessage(),
                f.auth.login(null, null).getMessage() };
            for (String m : messages) { check("Invalid username and/or password".equals(m), "generic login failure message"); }
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='UNKNOWN'") >= 1
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event='AUTH_FAILURE' AND username='UNKNOWN'") >= 1,
                    "malformed username records both validation and authentication failures");
            for (char[] malformed : new char[][] {new char[0], repeat('x', 65).toCharArray()}) {
                int before = Integer.parseInt(scalar(f.db, "SELECT failed_attempts FROM users WHERE username = ?", "client1"));
                check(AuthService.GENERIC_LOGIN_FAILURE.equals(f.auth.login("client1", malformed).getMessage()), "malformed known-account password retains generic feedback");
                check(Integer.parseInt(scalar(f.db, "SELECT failed_attempts FROM users WHERE username = ?", "client1")) == before + 1, "malformed known-account password increments the consecutive login-failure counter");
            }
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'VALIDATION_FAILURE' AND username = 'client1'") >= 2, "malformed known-account passwords are audited");
            check("0".equals(scalar(f.db, "SELECT COUNT(*) FROM logs WHERE \"desc\" LIKE ?", "%" + repeat('x', 65) + "%")), "oversized password is not persisted in audit details");
            check(f.auth.currentActor() == null, "no session after failures");
            for (int i = 0; i < 4; i++) { f.auth.login("staff1", "Wrong!Pass1".toCharArray()); }
            check("3".equals(f.role("staff1")), "malformed-input lockout case remains enabled after four valid-shaped failures");
            check(AuthService.GENERIC_LOGIN_FAILURE.equals(f.auth.login("staff1", repeat('x', 65).toCharArray()).getMessage())
                    && "1".equals(f.role("staff1")), "oversized password submission is the fifth consecutive failure and locks the known account");
        } finally { f.close(); }
    }

    private static void nonActiveAccountsSpendDummyPasswordWork() throws Exception {
        Fixture f = new Fixture();
        try {
            f.seed("disabledtiming", 1, GOOD);
            f.seed("lockedtiming", 3, GOOD);
            execute(f.db, "UPDATE users SET locked=1 WHERE username=?", "lockedtiming");
            final int[] invocations = {0};
            f.auth.setDummyPasswordVerifierForTest(new AuthService.DummyPasswordVerifier() {
                public void verify(char[] password) {
                    invocations[0]++;
                    PasswordHasher.verifyAgainstDummy(password);
                }
            });
            f.auth.login("missingtiming", GOOD.toCharArray());
            f.auth.login("disabledtiming", GOOD.toCharArray());
            f.auth.login("lockedtiming", GOOD.toCharArray());
            check(invocations[0] == 3,
                    "unknown, role-disabled, and temporarily locked valid-shaped attempts all invoke dummy PBKDF2 verification (observed " + invocations[0] + ")");
            f.auth.login("client1", "Wrong!Pass1".toCharArray());
            check(invocations[0] == 3, "active accounts continue to verify against their stored hash");
        } finally { f.close(); }
    }

    private static void malformedAndLockedLoginAttemptsAreHandledConsistently() throws Exception {
        Fixture f = new Fixture();
        try {
            int validationBefore = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='UNKNOWN'");
            int authBefore = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='AUTH_FAILURE' AND username='neverhere'");
            f.auth.login("neverhere", "No Secret".toCharArray());
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='UNKNOWN'") == validationBefore + 1
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event='AUTH_FAILURE' AND username='neverhere'") == authBefore + 1,
                    "malformed unknown-user password records validation and authentication failure");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE \"desc\" LIKE '%No Secret%'") == 0,
                    "malformed unknown-user audit records do not contain the submitted secret");

            f.seed("templock", 3, GOOD);
            execute(f.db, "UPDATE users SET locked=1, failed_attempts=3 WHERE username=?", "templock");
            f.auth.login("templock", new char[0]);
            f.auth.login("templock", "Wrong!Pass1".toCharArray());
            check("3".equals(scalar(f.db, "SELECT role FROM users WHERE username=?", "templock"))
                    && "3".equals(scalar(f.db, "SELECT failed_attempts FROM users WHERE username=?", "templock")),
                    "malformed and valid-shaped attempts against a temporary lock preserve its state");
            check("FAILED".equals(scalar(f.db, "SELECT last_attempt_status FROM users WHERE username=?", "templock"))
                    && scalar(f.db, "SELECT last_attempt_at FROM users WHERE username=?", "templock") != null,
                    "attempt against a temporary lock updates the previous-attempt status and timestamp");
            f.auth.login("admin1", GOOD.toCharArray());
            UserAdminService.Result unlock = f.admin.setLocked(f.id("templock"), false);
            check(unlock.isSuccess(), "authorized Admin unlocks the temporary lock through UserAdminService");
            check("0".equals(scalar(f.db, "SELECT failed_attempts FROM users WHERE username=?", "templock")),
                    "production unlock resets the consecutive login-failure counter");
            String blockedAt = scalar(f.db, "SELECT last_attempt_at FROM users WHERE username=?", "templock");
            check("FAILED".equals(scalar(f.db, "SELECT last_attempt_status FROM users WHERE username=?", "templock")) && blockedAt != null,
                    "production unlock preserves the blocked-attempt timestamp and status");
            f.auth.logout();
            AuthService.LoginResult afterUnlock = f.auth.login("templock", GOOD.toCharArray());
            String displayedBlockedAt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(blockedAt));
            check(afterUnlock.isSuccess() && afterUnlock.describePreviousAttempt().contains(displayedBlockedAt + " - FAILED"),
                    "successful login after production unlock reports the blocked attempt timestamp and status");
            check("SUCCESS".equals(scalar(f.db, "SELECT last_attempt_status FROM users WHERE username=?", "templock"))
                    && scalar(f.db, "SELECT last_attempt_at FROM users WHERE username=?", "templock") != null,
                    "successful post-unlock login stores its own timestamp and SUCCESS status");

            f.auth.login("client1", new char[0]);
            AuthService.LoginResult next = f.auth.login("client1", GOOD.toCharArray());
            check(next.isSuccess() && next.describePreviousAttempt().contains("FAILED")
                    && next.describePreviousAttempt().contains("20")
                    && next.describePreviousAttempt().contains("Failed attempts since your last successful login: 1"),
                    "next valid login reports malformed input as a timestamped failed attempt");
        } finally { f.close(); }
    }

    private static void lockoutHappensOnTheFifthConsecutiveFailureOnly() throws Exception {
        Fixture f = new Fixture();
        try {
            for (int i = 0; i < 4; i++) { f.auth.login("client1", "Wrong!Pass1".toCharArray()); }
            check("2".equals(f.role("client1")), "four failures do not disable the account");
            check(f.auth.login("client1", GOOD.toCharArray()).isSuccess(), "correct password after four failures succeeds");
            f.auth.logout();
            for (int i = 0; i < 4; i++) { f.auth.login("client1", "Wrong!Pass1".toCharArray()); }
            check("2".equals(f.role("client1")), "success reset the consecutive counter");
            f.auth.login("client1", "Wrong!Pass1".toCharArray());
            check("1".equals(f.role("client1")), "fifth consecutive failure moves the account to Role 1");
            check(!f.auth.login("client1", GOOD.toCharArray()).isSuccess(), "correct password is refused once disabled");
        } finally { f.close(); }
    }

    private static void disabledAccountCannotLoginEvenWithCorrectPassword() throws Exception {
        Fixture f = new Fixture();
        try {
            f.seed("locked1", 2, GOOD);
            execute(f.db, "UPDATE users SET locked = 1 WHERE username = ?", "locked1");
            check(!f.auth.login("locked1", GOOD.toCharArray()).isSuccess(), "manually locked account refused");
            execute(f.db, "UPDATE users SET locked = 0, role = 9 WHERE username = ?", "locked1");
            check(!f.auth.login("locked1", GOOD.toCharArray()).isSuccess(), "out-of-range role refused (fails closed)");
        } finally { f.close(); }
    }

    private static void lastLoginAttemptIsReportedOnSuccess() throws Exception {
        Fixture f = new Fixture();
        try {
            AuthService.LoginResult first = f.auth.login("client1", GOOD.toCharArray());
            check(first.isSuccess() && first.describePreviousAttempt().contains("No previous"), "first login reports no earlier attempt");
            f.auth.logout();
            f.auth.login("client1", "Wrong!Pass1".toCharArray());
            f.auth.login("client1", "Wrong!Pass1".toCharArray());
            AuthService.LoginResult next = f.auth.login("client1", GOOD.toCharArray());
            String text = next.describePreviousAttempt();
            check(text.contains("FAILED") && text.contains("20") && text.contains("Failed attempts since your last successful login: 2"), "report shows timestamp, failed status and count");
            f.auth.logout();
            check(f.auth.login("client1", GOOD.toCharArray()).describePreviousAttempt().contains("successful"), "report shows a previous success");
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ authorization

    private static void sessionIsRevalidatedAgainstTheDatabase() throws Exception {
        Fixture f = new Fixture();
        try {
            final int[] revoked = {0};
            f.auth.setSessionRevokedListener(new Runnable() { public void run() { revoked[0]++; } });
            f.auth.login("client1", GOOD.toCharArray());
            BundleBService.Actor actor = f.auth.currentActor();
            check(actor != null && actor.getRole() == 2 && actor.getUsername().equals("client1"), "actor reflects the signed-in account");
            check(f.access.check(AccessControl.Feature.PURCHASE) && !f.access.check(AccessControl.Feature.MANAGE_USERS), "role checked through the central component");
            execute(f.db, "UPDATE users SET role = 4 WHERE username = ?", "client1");
            check(f.auth.currentActor() == null && revoked[0] == 1, "role change mid-session ends the session");
            check(!f.access.check(AccessControl.Feature.READ_ALL_HISTORY), "no privileges after revocation");
            f.auth.login("client1", GOOD.toCharArray());
            execute(f.db, "DELETE FROM users WHERE username = ?", "client1");
            check(f.auth.currentActor() == null, "deleted account loses its session");
            check(!f.access.check(AccessControl.Feature.VIEW_CLIENT_HOME), "unauthenticated user has no home panel");
        } finally { f.close(); }
    }

    private static void roleTableMatchesTheAccessControlMatrix() {
        Object[][] expected = {
            {AccessControl.Feature.VIEW_ADMIN_HOME, "00001"}, {AccessControl.Feature.VIEW_MANAGER_HOME, "00010"},
            {AccessControl.Feature.VIEW_STAFF_HOME, "00100"}, {AccessControl.Feature.VIEW_CLIENT_HOME, "01000"},
            {AccessControl.Feature.MANAGE_USERS, "00001"}, {AccessControl.Feature.VIEW_LOGS, "00001"}, {AccessControl.Feature.MANAGE_LOGS, "00001"},
            {AccessControl.Feature.READ_CATALOG, "01110"}, {AccessControl.Feature.MANAGE_PRODUCTS, "00110"},
            {AccessControl.Feature.PURCHASE, "01000"}, {AccessControl.Feature.READ_OWN_HISTORY, "01000"}, {AccessControl.Feature.READ_ALL_HISTORY, "00010"} };
        for (Object[] row : expected) {
            String bits = (String) row[1];  // index 0 = role 1 ... index 4 = role 5
            for (int role = 1; role <= 5; role++) {
                check(AccessControl.roleMayUse(role, (AccessControl.Feature) row[0]) == (bits.charAt(role - 1) == '1'), row[0] + " for role " + role);
            }
            check(!AccessControl.roleMayUse(0, (AccessControl.Feature) row[0]) && !AccessControl.roleMayUse(6, (AccessControl.Feature) row[0]), row[0] + " denies unknown roles");
        }
        check(AccessControl.homeFor(1) == null && AccessControl.homeFor(5) == AccessControl.Feature.VIEW_ADMIN_HOME, "role to home mapping");
    }

    private static void injectionShapedInputIsRejectedNotSanitized() throws Exception {
        Fixture f = new Fixture();
        try {
            String[] attacks = {"admin' OR '1'='1", "admin'--", "x'; DROP TABLE users;--", "<script>alert(1)</script>", "admin\u0000"};
            for (String attack : attacks) {
                check(!f.auth.login(attack, GOOD.toCharArray()).isSuccess(), "login with injection-shaped username refused");
                check(!f.auth.register(attack, GOOD.toCharArray(), GOOD.toCharArray()).isSuccess(), "registration with injection-shaped username refused");
            }
            check(!f.auth.login("client1", "' OR '1'='1".toCharArray()).isSuccess(), "injection-shaped password refused");
            check(count(f.db, "SELECT COUNT(*) FROM users") == 5, "users table intact after attack strings");
            check(!f.auth.register(" newuser ", GOOD.toCharArray(), GOOD.toCharArray()).isSuccess() && count(f.db, "SELECT COUNT(*) FROM users WHERE username LIKE '%newuser%'") == 0,
                    "whitespace is rejected rather than trimmed");
        } finally { f.close(); }
    }

    private static void reauthenticationFailuresAreSeparateFromLoginLockout() throws Exception {
        Fixture f = new Fixture();
        try {
            final int[] revoked = {0};
            f.auth.setSessionRevokedListener(new Runnable() { public void run() { revoked[0]++; } });
            f.auth.login("client1", GOOD.toCharArray());
            BundleBService.Actor actor = f.auth.currentActor();
            check(f.auth.verifyCurrentPassword(actor, GOOD.toCharArray()), "correct current password verifies");
            check(!f.auth.verifyCurrentPassword(actor, "Wrong!Pass1".toCharArray()), "wrong current password fails");
            int validationBefore = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='client1'");
            int authFailureBefore = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='AUTH_FAILURE' AND username='client1'");
            check(!f.auth.verifyCurrentPassword(actor, "bad secret".toCharArray()), "malformed current password fails");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='client1'") == validationBefore + 1
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event='AUTH_FAILURE' AND username='client1'") == authFailureBefore + 1,
                    "malformed re-authentication records both validation and authentication failures");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE \"desc\" LIKE '%bad secret%'") == 0
                    && "0".equals(scalar(f.db, "SELECT failed_attempts FROM users WHERE username='client1'")),
                    "malformed re-authentication does not log password data or increment login failures");
            check(!f.auth.verifyCurrentPassword(new BundleBService.Actor(actor.getUserId(), actor.getUsername(), actor.getRole(), actor.getSessionGeneration() + 1), GOOD.toCharArray()), "stale actor cannot re-authenticate");
            for (int i = 0; i < 4; i++) { f.auth.verifyCurrentPassword(actor, "Wrong!Pass1".toCharArray()); }
            check("2".equals(f.role("client1")) && f.auth.currentActor() != null,
                    "failed re-authentication does not count as a failed login or disable the account");
            check("5".equals(scalar(f.db, "SELECT reauth_failures FROM users WHERE username = ?", "client1")),
                    "reauthentication failures are tracked separately");
            check(f.auth.verifyCurrentPassword(actor, GOOD.toCharArray())
                    && "0".equals(scalar(f.db, "SELECT reauth_failures FROM users WHERE username = ?", "client1")),
                    "successful re-authentication resets its own failure count");
            for (int i = 0; i < 4; i++) { f.auth.login("client1", "Wrong!Pass1".toCharArray()); }
            check("2".equals(f.role("client1")), "reauthentication failures do not affect five-login lockout threshold");
            f.auth.login("client1", "Wrong!Pass1".toCharArray());
            check("1".equals(f.role("client1")) && revoked[0] == 1 && f.auth.currentActor() == null,
                    "exactly five consecutive failed logins disable the account and end the session");
        } finally { f.close(); }
    }

    private static void adminOperationsAreAuthorizedValidatedAndReauthenticated() throws Exception {
        Fixture f = new Fixture();
        try {
            f.auth.login("client1", GOOD.toCharArray());
            check(f.admin.listUsers().isEmpty(), "client cannot list users");
            check(!f.admin.changeRole(f.id("client2"), "5", GOOD.toCharArray()).isSuccess() && "2".equals(f.role("client2")), "client cannot change roles");
            check(!f.admin.deleteUser(f.id("client2"), GOOD.toCharArray()).isSuccess() && f.id("client2") > 0, "client cannot delete users");
            f.auth.logout();

            f.auth.login("admin1", GOOD.toCharArray());
            List<UserAdminService.UserRow> rows = f.admin.listUsers();
            check(rows.size() == 5, "admin lists users (no password data in rows)");
            int client2 = f.id("client2");
            for (String badRole : new String[] {"0", "6", "-1", "abc", "", "99", null}) {
                check(!f.admin.changeRole(client2, badRole, GOOD.toCharArray()).isSuccess() && "2".equals(f.role("client2")), "out-of-range role rejected: " + badRole);
            }
            check(!f.admin.changeRole(client2, "3", "Wrong!Pass1".toCharArray()).isSuccess() && "2".equals(f.role("client2")), "role change needs the admin's current password");
            check(f.admin.changeRole(client2, "3", GOOD.toCharArray()).isSuccess() && "3".equals(f.role("client2")), "role change with valid input and password");
            check(!f.admin.changeRole(f.id("admin1"), "2", GOOD.toCharArray()).isSuccess() && "5".equals(f.role("admin1")), "admin cannot demote self");
            check(!f.admin.deleteUser(f.id("admin1"), GOOD.toCharArray()).isSuccess(), "admin cannot delete self");
            check(!f.admin.changeRole(99999, "2", GOOD.toCharArray()).isSuccess(), "unknown user id fails safely");

            check(!f.admin.changePassword(client2, "weak".toCharArray(), "weak".toCharArray(), GOOD.toCharArray()).isSuccess(), "weak new password rejected");
            check(!f.admin.changePassword(client2, "N3w!Passw0rd".toCharArray(), "Other!Pass1".toCharArray(), GOOD.toCharArray()).isSuccess(), "mismatched new password rejected");
            check(!f.admin.changePassword(client2, "N3w!Passw0rd".toCharArray(), "N3w!Passw0rd".toCharArray(), "Wrong!Pass1".toCharArray()).isSuccess(), "password change needs re-authentication");
            check(f.admin.changePassword(client2, "N3w!Passw0rd".toCharArray(), "N3w!Passw0rd".toCharArray(), GOOD.toCharArray()).isSuccess(), "password change succeeds");
            String stored = scalar(f.db, "SELECT password FROM users WHERE username = ?", "client2");
            check(PasswordHasher.isHashFormat(stored) && !stored.contains("N3w!Passw0rd"), "changed password stored as a hash");
            check(f.admin.setLocked(client2, true).isSuccess() && !f.auth.login("client2", "N3w!Passw0rd".toCharArray()).isSuccess(), "locked account cannot sign in");
            check(f.admin.setLocked(client2, false).isSuccess(), "account unlocked");

            f.seed("victim1", 2, GOOD);
            for (int i = 0; i < 5; i++) { f.auth.login("victim1", "Wrong!Pass1".toCharArray()); }
            check("1".equals(f.role("victim1")), "victim disabled by failures");
            // login() above replaced the admin session only on success; admin session remains
            check(f.admin.changeRole(f.id("victim1"), "2", GOOD.toCharArray()).isSuccess(), "admin re-enables a disabled account");
            check(f.auth.login("victim1", GOOD.toCharArray()).isSuccess(), "re-enabled account can sign in with a fresh failure counter");
        } finally { f.close(); }
    }

    private static void bundleBWorksThroughTheRealGateway() throws Exception {
        Fixture f = new Fixture();
        try {
            final int[] fallbacks = {0};
            BundleBService b = new BundleBService(f.db, new AppSecurityGateway(f.auth, new Runnable() { public void run() { fallbacks[0]++; } }));
            b.initialize();
            check(b.listCatalog().getStatus() == BundleBService.Status.DENIED, "no session: catalog denied");
            f.auth.login("staff1", GOOD.toCharArray());
            check(b.canManageProducts() && !b.canPurchase(), "staff may manage products but not purchase");
            check(b.addProduct("Widget", "5", "2.50", "Wrong!Pass1".toCharArray()).getStatus() == BundleBService.Status.REAUTH_REQUIRED, "B re-authentication rejects a wrong password");
            check(b.addProduct("Widget", "5", "2.50", GOOD.toCharArray()).isSuccess(), "B re-authentication accepts the real password");
            execute(f.db, "CREATE TRIGGER reject_product_audit BEFORE INSERT ON logs WHEN NEW.event = 'PRODUCT_ADD' BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END");
            check(!b.addProduct("AuditBlocked", "1", "1.00", GOOD.toCharArray()).isSuccess(), "actual SQLite audit insert failure rejects product mutation");
            check(count(f.db, "SELECT COUNT(*) FROM product WHERE name = 'AuditBlocked'") == 0 && f.auth.currentActor() == null,
                    "actual audit insert failure rolls back product change and revokes the session");
            execute(f.db, "DROP TRIGGER reject_product_audit");
            f.auth.logout();
            f.auth.login("client1", GOOD.toCharArray());
            check(b.canPurchase() && !b.canManageProducts(), "client may purchase but not manage products");
            check(b.addProduct("Other", "1", "1.00", GOOD.toCharArray()).getStatus() == BundleBService.Status.DENIED, "client cannot add products");
            int productId = b.listCatalog().getValue().get(0).getId();
            check(b.confirmPurchase(b.quotePurchase(productId, "1").getValue()).isSuccess(), "client purchase succeeds");
            check(b.historyForCurrentActor("").getValue().size() == 1, "client sees own history");
            f.auth.logout();
            f.auth.login("manager1", GOOD.toCharArray());
            check(b.canViewHistory() && b.historyForCurrentActor("").getValue().size() == 1,
                    "Manager session through the real gateway can view all-user purchase history");
            f.auth.logout();
            f.auth.login("admin1", GOOD.toCharArray());
            check(b.listCatalog().getStatus() == BundleBService.Status.DENIED && !b.canViewHistory(), "admin has no product or history access");
            f.auth.logout();
            for (int i = 0; i < 5; i++) { f.auth.login("manager1", "Wrong!Pass1".toCharArray()); }
            f.auth.login("manager1", GOOD.toCharArray());
            check(b.listCatalog().getStatus() == BundleBService.Status.DENIED, "disabled manager has no access");
            new AppSecurityGateway(f.auth, new Runnable() { public void run() { fallbacks[0]++; } }).onUnrecoverableFailure();
            check(fallbacks[0] >= 2 && f.auth.currentActor() == null, "unrecoverable failure ends the session and returns to the safe state");
        } finally { f.close(); }
    }

    private static void failsClosedWhenStorageIsUnavailable() throws Exception {
        Path dir = Files.createTempDirectory("auth-missing-");
        try {
            AuthService auth = new AuthService(dir.resolve("no").resolve("such").resolve("db.sqlite"), SecurityAudit.NONE);
            AuthService.LoginResult r = auth.login("client1", GOOD.toCharArray());
            check(!r.isSuccess() && r.getMessage().length() > 0 && !r.getMessage().contains("SQL") && !r.getMessage().contains("Exception"), "login fails closed with a non-technical message");
            check(auth.currentActor() == null && !new AccessControl(auth).check(AccessControl.Feature.VIEW_CLIENT_HOME), "no privileges without storage");
            check(!auth.register("newuser", GOOD.toCharArray(), GOOD.toCharArray()).isSuccess(), "registration fails closed");
            boolean threw = false;
            try { auth.initialize(); } catch (IllegalStateException expected) { threw = !expected.getMessage().contains("sqlite"); }
            check(threw, "initialize reports a generic failure");
        } finally { Files.deleteIfExists(dir); }
    }

    private static void unexpectedDatabaseFailuresRevokeSessionsAndDenyControllers() throws Exception {
        Fixture f = new Fixture();
        try {
            check(f.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin session established before unexpected database failure");
            final int[] safeFallbacks = {0};
            f.auth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
            execute(f.db, "DROP TABLE users");
            check(f.auth.currentActor() == null && safeFallbacks[0] == 1,
                    "unexpected account-storage query failure revokes the active session and routes through the safe-state listener");
            check(f.admin.listUsers().isEmpty() && !f.admin.changeRole(2, "3", GOOD.toCharArray()).isSuccess(),
                    "user administration returns safe empty or denied outcomes after storage failure");
        } finally { f.close(); }
    }

    private static void authenticationRollsBackWhenAuditPersistenceFails() throws Exception {
        Fixture f = new Fixture();
        try {
            SecurityAudit unavailable = new SecurityAudit() {
                public void record(String event, String username, String detail) {
                    throw new IllegalStateException("audit storage unavailable");
                }
                public void record(Connection connection, String event, String username, String detail) {
                    throw new IllegalStateException("audit storage unavailable");
                }
            };
            AuthService auth = new AuthService(f.db, unavailable);
            auth.initialize();

            AuthService.LoginResult failed = auth.login("client1", "Wrong!Pass1".toCharArray());
            check(failed.getStatus() == AuthService.LoginResult.Status.ERROR,
                    "failed sign-in does not report success when its required audit write fails");
            check("0".equals(scalar(f.db, "SELECT failed_attempts FROM users WHERE username = ?", "client1")),
                    "failed sign-in counter rolls back when audit persistence fails");
            check(scalar(f.db, "SELECT last_attempt_at FROM users WHERE username = ?", "client1") == null,
                    "failed attempt state rolls back when its audit record cannot persist");

            AuthService.LoginResult success = auth.login("client1", GOOD.toCharArray());
            check(success.getStatus() == AuthService.LoginResult.Status.ERROR && auth.currentActor() == null,
                    "successful credentials do not create a session when audit persistence fails");
            check(scalar(f.db, "SELECT last_attempt_at FROM users WHERE username = ?", "client1") == null,
                    "successful attempt state rolls back when audit persistence fails");

            SqliteSecurityAudit recoveredAudit = new SqliteSecurityAudit(f.db);
            recoveredAudit.initialize();
            AuthService recovered = new AuthService(f.db, recoveredAudit);
            recovered.initialize();
            check(recovered.login("client1", GOOD.toCharArray()).isSuccess(),
                    "sign-in succeeds after audit storage recovers");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_SUCCESS'") == 1,
                    "the recovered sign-in is persisted exactly once");

            final boolean[] failAuthenticationAudit = {false};
            final SqliteSecurityAudit persistent = new SqliteSecurityAudit(f.db);
            SecurityAudit intermittentlyUnavailable = new SecurityAudit() {
                public void record(String event, String username, String detail) {
                    if (failAuthenticationAudit[0] && "AUTH_FAILURE".equals(event)) { throw new IllegalStateException("audit unavailable"); }
                    persistent.record(event, username, detail);
                }
                public void record(Connection connection, String event, String username, String detail) {
                    if (failAuthenticationAudit[0] && "AUTH_FAILURE".equals(event)) { throw new IllegalStateException("audit unavailable"); }
                    persistent.record(connection, event, username, detail);
                }
            };
            AuthService sessionAuth = new AuthService(f.db, intermittentlyUnavailable);
            persistent.bindTrustedAuth(sessionAuth);
            sessionAuth.initialize();
            check(sessionAuth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in before malformed-input audit outage");
            final int[] safeFallbacks = {0};
            sessionAuth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
            int validationRowsBefore = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='UNKNOWN'");
            failAuthenticationAudit[0] = true;
            AuthService.LoginResult malformed = sessionAuth.login("bad name", GOOD.toCharArray());
            check(malformed.getStatus() == AuthService.LoginResult.Status.ERROR
                    && sessionAuth.currentActor() == null && safeFallbacks[0] == 1,
                    "failed second audit for malformed username rolls back and revokes the existing session to safe state");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='UNKNOWN'") == validationRowsBefore,
                    "malformed username validation event rolls back if its paired authentication event cannot persist");
        } finally { f.close(); }
    }

    private static void auditProviderPersistsFiltersAndRestrictsLogReview() throws Exception {
        Fixture f = new Fixture();
        try {
            SqliteSecurityAudit audit = new SqliteSecurityAudit(f.db);
            audit.initialize();
            AuthService admin = new AuthService(f.db, audit);
            audit.bindTrustedAuth(admin);
            admin.initialize();
            admin.login("bad name", GOOD.toCharArray());
            check(!admin.login("admin1", "Wrong!Pass1".toCharArray()).isSuccess(), "invalid sign-in is denied");
            check(admin.login("admin1", GOOD.toCharArray()).isSuccess(), "admin can sign in after a recorded failure");
            BundleBService.Actor adminActor = admin.currentActor();
            check(admin.verifyCurrentPassword(adminActor, GOOD.toCharArray()), "valid re-authentication succeeds");
            check(!admin.verifyCurrentPassword(adminActor, "Wrong!Pass1".toCharArray()), "invalid re-authentication is denied");

            List<Logs> matching = audit.search(new AccessControl(admin), "invalid password", "AUTH_FAILURE");
            check(matching.size() == 1 && "admin1".equals(matching.get(0).getUsername()),
                    "Admin search filters persisted audit events by text and event type");
            check(matching.get(0).getTimestamp() != null, "persisted ISO audit timestamps are readable");

            Fixture foreign = new Fixture();
            try {
                check(foreign.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "separate database Admin signs in");
                admin.logout();
                check(audit.search(new AccessControl(foreign.auth), "", null).isEmpty(),
                        "Admin session from another database cannot read target audit records");
                check(admin.login("admin1", GOOD.toCharArray()).isSuccess()
                        && audit.search(new AccessControl(foreign.auth), "", null).size() == count(f.db, "SELECT COUNT(*) FROM logs"),
                        "target database Admin session, not the supplied provider, authorizes review");
                try { audit.bindTrustedAuth(foreign.auth); throw new AssertionError("cross-database trusted provider binding rejected"); }
                catch (IllegalArgumentException expected) { check(true, "audit provider refuses an auth provider for another database"); }
            } finally { foreign.close(); }

            final SqliteSecurityAudit uiAudit = audit;
            SQLite uiStore = new SQLite() {
                @Override public ArrayList<Logs> getLogs(AccessControl access, String text, String event) {
                    return new ArrayList<Logs>(uiAudit.search(access, text, event));
                }
            };
            MgmtLogs adminLogPanel = new MgmtLogs(uiStore, new AccessControl(admin));
            adminLogPanel.init();
            int allRows = adminLogPanel.tableModel.getRowCount();
            check(allRows == count(f.db, "SELECT COUNT(*) FROM logs"),
                    "Admin log panel initially displays the persisted rows");
            adminLogPanel.searchLogs("invalid password");
            check(adminLogPanel.tableModel.getRowCount() == 1
                    && "AUTH_FAILURE".equals(adminLogPanel.tableModel.getValueAt(0, 0)),
                    "Admin log panel displays the requested filtered rows");
            int rowsBeforeInjectionSearch = count(f.db, "SELECT COUNT(*) FROM logs");
            adminLogPanel.searchLogs("' OR 1=1 --");
            check(adminLogPanel.tableModel.getRowCount() == 0
                    && count(f.db, "SELECT COUNT(*) FROM logs") == rowsBeforeInjectionSearch,
                    "search-shaped text returns no unrelated rows and does not change stored logs");
            adminLogPanel.showAllLogs();
            check(adminLogPanel.tableModel.getRowCount() == allRows,
                    "Show All restores the complete log view without deleting rows");

            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'VALIDATION_FAILURE'") > 0,
                    "invalid authentication input is persisted as a validation event");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_SUCCESS'") >= 2
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_FAILURE'") >= 2,
                    "login and re-authentication successes and failures are persisted");
            check(audit.search(new AccessControl(admin), "' OR 1=1 --", null).isEmpty(),
                    "search text is treated as data, not SQL");
            int beforeInvalidEventFilter = count(f.db, "SELECT COUNT(*) FROM logs");
            int failedAuthBeforeInvalidFilter = count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_FAILURE'");
            check(audit.search(new AccessControl(admin), "", "AUTH_FAILURE OR 1=1").isEmpty(),
                    "invalid event filters are rejected without returning rows");
            check(count(f.db, "SELECT COUNT(*) FROM logs") == beforeInvalidEventFilter + 1
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_FAILURE'") == failedAuthBeforeInvalidFilter,
                    "invalid filter audit adds only its validation event and leaves existing records intact");
            check("0".equals(scalar(f.db, "SELECT COUNT(*) FROM logs WHERE \"desc\" LIKE ?", "%" + GOOD + "%")),
                    "audit records do not contain submitted credentials");

            admin.logout();
            check(admin.login("client1", GOOD.toCharArray()).isSuccess(), "client can sign in for denied-review test");
            check(audit.search(new AccessControl(admin), "", null).isEmpty(),
                    "non-admin direct log review returns no records");
            MgmtLogs clientLogPanel = new MgmtLogs(uiStore, new AccessControl(admin));
            clientLogPanel.searchLogs("invalid password");
            check(clientLogPanel.tableModel.getRowCount() == 0,
                    "non-admin log panel remains empty after direct UI-boundary search");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'ACCESS_DENIED'") > 0,
                    "unauthorized log review is audited");
            check(audit.search(null, "", null).isEmpty(), "direct review without an access provider is denied");

            int beforeUpgrade = count(f.db, "SELECT COUNT(*) FROM logs");
            audit.initialize();
            check(count(f.db, "SELECT COUNT(*) FROM logs") == beforeUpgrade && count(f.db, "SELECT COUNT(*) FROM users") == 5,
                    "repeated audit initialization preserves logs and existing application data");

            admin.logout();
            check(admin.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in for audit query outage UI check");
            adminLogPanel.searchLogs("");
            execute(f.db, "DROP TABLE logs");
            boolean queryFailed = false;
            try { uiStore.getLogs(new AccessControl(admin), "", ""); }
            catch (IllegalStateException expected) { queryFailed = "Security logs are unavailable.".equals(expected.getMessage()); }
            check(queryFailed, "public SQLite.getLogs reports a generic failure instead of false empty success");
            adminLogPanel.searchLogs("");
            check(adminLogPanel.tableModel.getRowCount() == 0
                    && hasLabelText(adminLogPanel, "Audit records are unavailable right now."),
                    "Admin log UI clears stale records and shows a safe outage message when query storage fails");
        } finally { f.close(); }
    }

    private static boolean hasLabelText(java.awt.Container container, String expected) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.JLabel && expected.equals(((javax.swing.JLabel) component).getText())) { return true; }
            if (component instanceof java.awt.Container && hasLabelText((java.awt.Container) component, expected)) { return true; }
        }
        return false;
    }

    private static void registrationRollsBackWhenAuditPersistenceFails() throws Exception {
        Fixture f = new Fixture();
        try {
            SecurityAudit unavailableForRegistration = new SecurityAudit() {
                public void record(String event, String username, String detail) {
                    throw new IllegalStateException("audit storage unavailable");
                }
                public void record(Connection connection, String event, String username, String detail) {
                    throw new IllegalStateException("audit storage unavailable");
                }
            };
            AuthService auth = new AuthService(f.db, unavailableForRegistration);
            auth.initialize();

            AuthService.RegistrationResult result = auth.register("newclient", GOOD.toCharArray(), GOOD.toCharArray());
            check(!result.isSuccess(), "registration reports failure when its required audit write fails");
            check("0".equals(scalar(f.db, "SELECT COUNT(*) FROM users WHERE username = ?", "newclient")),
                    "new account creation rolls back when its audit record cannot persist");
        } finally { f.close(); }
    }

    private static void userAdministrationRollsBackWhenAuditPersistenceFails() throws Exception {
        for (final String operation : new String[] {"role", "lock", "unlock", "delete", "password"}) {
            Fixture f = new Fixture();
            try {
                check(f.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in before " + operation + " failure");
                final int[] safeFallbacks = {0};
                f.auth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
                execute(f.db, "CREATE TRIGGER refuse_user_admin BEFORE INSERT ON logs WHEN NEW.event='USER_ADMIN' BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END");
                UserAdminService.Result result;
                if ("role".equals(operation)) { result = f.admin.changeRole(f.id("client2"), "3", GOOD.toCharArray()); }
                else if ("lock".equals(operation)) { result = f.admin.setLocked(f.id("client2"), true); }
                else if ("unlock".equals(operation)) {
                    execute(f.db, "UPDATE users SET locked=1 WHERE username='client2'");
                    result = f.admin.setLocked(f.id("client2"), false);
                } else if ("delete".equals(operation)) { result = f.admin.deleteUser(f.id("client2"), GOOD.toCharArray()); }
                else { result = f.admin.changePassword(f.id("client2"), "Other!Pass2".toCharArray(), "Other!Pass2".toCharArray(), GOOD.toCharArray()); }
                check(!result.isSuccess() && "The action could not be completed.".equals(result.getMessage()),
                        operation + " returns a generic failure when audit persistence fails");
                check("2".equals(f.role("client2")) && "0".equals(scalar(f.db, "SELECT COUNT(*) FROM logs WHERE event='USER_ADMIN'")),
                        operation + " mutation rolls back and leaves no success audit record");
                if ("lock".equals(operation)) { check("0".equals(scalar(f.db, "SELECT locked FROM users WHERE username='client2'")), "failed lock rolls back"); }
                if ("unlock".equals(operation)) { check("1".equals(scalar(f.db, "SELECT locked FROM users WHERE username='client2'")), "failed unlock rolls back"); }
                if ("delete".equals(operation)) { check("1".equals(scalar(f.db, "SELECT COUNT(*) FROM users WHERE username='client2'")), "failed delete rolls back"); }
                if ("password".equals(operation)) { check(!PasswordHasher.verify("Other!Pass2".toCharArray(), scalar(f.db, "SELECT password FROM users WHERE username='client2'")), "failed password change rolls back"); }
                check(f.auth.currentActor() == null && safeFallbacks[0] == 1,
                        operation + " audit failure revokes Admin session and invokes safe-state fallback");
            } finally { f.close(); }
        }
    }

    private static void userAdministrationValidationAuditFailureRevokesSession() throws Exception {
        for (final String operation : new String[] {"role", "lock", "delete", "password-policy", "password-confirmation"}) {
            Fixture f = new Fixture();
            try {
                check(f.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in before " + operation + " validation-audit failure");
                final int[] safeFallbacks = {0};
                f.auth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
                execute(f.db, "DROP TABLE logs");
                int clientId = f.id("client2");
                UserAdminService.Result result;
                if ("role".equals(operation)) { result = f.admin.changeRole(clientId, "invalid", GOOD.toCharArray()); }
                else if ("lock".equals(operation)) { result = f.admin.setLocked(-1, true); }
                else if ("delete".equals(operation)) { result = f.admin.deleteUser(-1, GOOD.toCharArray()); }
                else if ("password-policy".equals(operation)) {
                    result = f.admin.changePassword(clientId, "weak".toCharArray(), "weak".toCharArray(), GOOD.toCharArray());
                } else {
                    result = f.admin.changePassword(clientId, GOOD.toCharArray(), "Other!Pass1".toCharArray(), GOOD.toCharArray());
                }
                check(!result.isSuccess() && "The action could not be completed.".equals(result.getMessage()),
                        operation + " returns a generic failure when standalone validation audit persistence fails");
                check(f.auth.currentActor() == null && safeFallbacks[0] == 1,
                        operation + " standalone validation audit failure revokes Admin session and invokes safe fallback");
            } finally { f.close(); }
        }
    }

    private static void reauthenticationAuditFailureRevokesSession() throws Exception {
        for (final boolean validPassword : new boolean[] {true, false}) {
            Fixture f = new Fixture();
            try {
                check(f.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in before re-authentication audit failure");
                execute(f.db, "UPDATE users SET reauth_failures=2 WHERE username='admin1'");
                final int[] safeFallbacks = {0};
                f.auth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
                final String detail = validPassword ? "re-authentication" : "re-authentication failed";
                execute(f.db, "CREATE TRIGGER refuse_reauth BEFORE INSERT ON logs WHEN NEW.\"desc\"='" + detail
                        + "' BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END");
                boolean failed = false;
                boolean generic = false;
                try { f.auth.verifyCurrentPassword(f.auth.currentActor(), (validPassword ? GOOD : "Wrong!Pass1").toCharArray()); }
                catch (IllegalStateException expected) { failed = true; generic = "Security audit storage is unavailable.".equals(expected.getMessage()); }
                check(failed && generic && f.auth.currentActor() == null && safeFallbacks[0] == 1,
                        "required " + detail + " audit failure revokes Admin session and invokes safe fallback");
                check("2".equals(scalar(f.db, "SELECT reauth_failures FROM users WHERE username='admin1'")),
                        "failed " + detail + " audit rolls back its associated re-authentication update");
            } finally { f.close(); }
        }
        Fixture f = new Fixture();
        try {
            check(f.auth.login("admin1", GOOD.toCharArray()).isSuccess(), "Admin signs in before malformed re-authentication audit failure");
            final int[] safeFallbacks = {0};
            f.auth.setSessionRevokedListener(new Runnable() { public void run() { safeFallbacks[0]++; } });
            execute(f.db, "CREATE TRIGGER refuse_invalid_reauth BEFORE INSERT ON logs WHEN NEW.event='AUTH_FAILURE' AND NEW.\"desc\"='invalid re-authentication input' BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END");
            int validations = count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='admin1'");
            int loginFailures = Integer.parseInt(scalar(f.db, "SELECT failed_attempts FROM users WHERE username='admin1'"));
            boolean failed = false;
            try { f.auth.verifyCurrentPassword(f.auth.currentActor(), "bad secret".toCharArray()); }
            catch (IllegalStateException expected) { failed = "Verification unavailable.".equals(expected.getMessage()); }
            check(failed && f.auth.currentActor() == null && safeFallbacks[0] == 1,
                    "required malformed re-authentication audit failure revokes the session and invokes safe fallback");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event='VALIDATION_FAILURE' AND username='admin1'") == validations
                    && Integer.parseInt(scalar(f.db, "SELECT failed_attempts FROM users WHERE username='admin1'")) == loginFailures,
                    "malformed re-authentication audit is atomic and does not alter the login-failure counter");
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ helpers

    private static final class Fixture {
        final Path db; final AuthService auth; final AccessControl access; final UserAdminService admin;
        Fixture() throws Exception {
            db = tempDb();
            SqliteSecurityAudit audit = new SqliteSecurityAudit(db);
            audit.initialize();
            auth = new AuthService(db, audit);
            audit.bindTrustedAuth(auth);
            auth.initialize();
            access = new AccessControl(auth);
            admin = new UserAdminService(auth, access);
            seed("client1", 2, GOOD); seed("client2", 2, GOOD);
            seed("staff1", 3, GOOD); seed("manager1", 4, GOOD); seed("admin1", 5, GOOD);
        }
        void seed(String name, int role, String password) throws Exception {
            try (Connection c = open(db)) {
                AccountStore store = auth.store();
                if (store.findByUsername(c, name) == null) { store.insertClient(c, name, PasswordHasher.hash(password.toCharArray())); }
                store.updateRole(c, store.findByUsername(c, name).id, role);
            }
        }
        String role(String name) throws Exception { return scalar(db, "SELECT role FROM users WHERE username = ?", name); }
        int id(String name) throws Exception { String v = scalar(db, "SELECT id FROM users WHERE username = ?", name); return v == null ? -1 : Integer.parseInt(v); }
        void close() throws Exception { Files.deleteIfExists(db); }
    }

    private static Path tempDb() throws Exception { return Files.createTempFile("bundle-a-", ".db"); }
    private static Connection open(Path db) throws Exception { return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath()); }

    private static void execute(Path db, String sql, Object... params) throws Exception {
        try (Connection c = open(db); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) { s.setObject(i + 1, params[i]); }
            s.executeUpdate();
        }
    }

    private static String scalar(Path db, String sql, Object... params) throws Exception {
        try (Connection c = open(db); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) { s.setObject(i + 1, params[i]); }
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private static int count(Path db, String sql) throws Exception { return Integer.parseInt(scalar(db, sql)); }
    private static boolean allZero(char[] v) { for (char c : v) { if (c != 0) { return false; } } return true; }
    private static String repeat(char c, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) { b.append(c); } return b.toString(); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError("FAILED: " + message); }
    }
}
