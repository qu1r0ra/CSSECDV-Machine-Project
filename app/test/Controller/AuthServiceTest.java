package Controller;

import Model.Logs;
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
        lockoutHappensOnTheFifthConsecutiveFailureOnly();
        disabledAccountCannotLoginEvenWithCorrectPassword();
        lastLoginAttemptIsReportedOnSuccess();
        sessionIsRevalidatedAgainstTheDatabase();
        roleTableMatchesTheAccessControlMatrix();
        injectionShapedInputIsRejectedNotSanitized();
        reauthenticationCountsTowardLockoutAndEndsTheSession();
        adminOperationsAreAuthorizedValidatedAndReauthenticated();
        bundleBWorksThroughTheRealGateway();
        authenticationRollsBackWhenAuditPersistenceFails();
        registrationRollsBackWhenAuditPersistenceFails();
        userAdministrationRollsBackWhenAuditPersistenceFails();
        auditProviderPersistsFiltersAndRestrictsLogReview();
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
            check(f.auth.currentActor() == null, "no session after failures");
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

    private static void reauthenticationCountsTowardLockoutAndEndsTheSession() throws Exception {
        Fixture f = new Fixture();
        try {
            final int[] revoked = {0};
            f.auth.setSessionRevokedListener(new Runnable() { public void run() { revoked[0]++; } });
            f.auth.login("client1", GOOD.toCharArray());
            BundleBService.Actor actor = f.auth.currentActor();
            check(f.auth.verifyCurrentPassword(actor, GOOD.toCharArray()), "correct current password verifies");
            check(!f.auth.verifyCurrentPassword(actor, "Wrong!Pass1".toCharArray()), "wrong current password fails");
            check(!f.auth.verifyCurrentPassword(new BundleBService.Actor(actor.getUserId(), actor.getUsername(), actor.getRole(), actor.getSessionGeneration() + 1), GOOD.toCharArray()), "stale actor cannot re-authenticate");
            for (int i = 0; i < 4; i++) { f.auth.verifyCurrentPassword(actor, "Wrong!Pass1".toCharArray()); }
            check("1".equals(f.role("client1")) && revoked[0] == 1 && f.auth.currentActor() == null, "five failed re-authentications disable the account and end the session");
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
            f.auth.logout();
            f.auth.login("client1", GOOD.toCharArray());
            check(b.canPurchase() && !b.canManageProducts(), "client may purchase but not manage products");
            check(b.addProduct("Other", "1", "1.00", GOOD.toCharArray()).getStatus() == BundleBService.Status.DENIED, "client cannot add products");
            int productId = b.listCatalog().getValue().get(0).getId();
            check(b.confirmPurchase(b.quotePurchase(productId, "1").getValue()).isSuccess(), "client purchase succeeds");
            check(b.historyForCurrentActor("").getValue().size() == 1, "client sees own history");
            f.auth.logout();
            f.auth.login("admin1", GOOD.toCharArray());
            check(b.listCatalog().getStatus() == BundleBService.Status.DENIED && !b.canViewHistory(), "admin has no product or history access");
            f.auth.logout();
            for (int i = 0; i < 5; i++) { f.auth.login("manager1", "Wrong!Pass1".toCharArray()); }
            f.auth.login("manager1", GOOD.toCharArray());
            check(b.listCatalog().getStatus() == BundleBService.Status.DENIED, "disabled manager has no access");
            new AppSecurityGateway(f.auth, new Runnable() { public void run() { fallbacks[0]++; } }).onUnrecoverableFailure();
            check(fallbacks[0] == 1 && f.auth.currentActor() == null, "unrecoverable failure ends the session and returns to the safe state");
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
        } finally { f.close(); }
    }

    private static void auditProviderPersistsFiltersAndRestrictsLogReview() throws Exception {
        Fixture f = new Fixture();
        try {
            SqliteSecurityAudit audit = new SqliteSecurityAudit(f.db);
            audit.initialize();
            AuthService admin = new AuthService(f.db, audit);
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
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'VALIDATION_FAILURE'") > 0,
                    "invalid authentication input is persisted as a validation event");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_SUCCESS'") >= 2
                    && count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'AUTH_FAILURE'") >= 2,
                    "login and re-authentication successes and failures are persisted");
            check(audit.search(new AccessControl(admin), "' OR 1=1 --", null).isEmpty(),
                    "search text is treated as data, not SQL");
            check("0".equals(scalar(f.db, "SELECT COUNT(*) FROM logs WHERE \"desc\" LIKE ?", "%" + GOOD + "%")),
                    "audit records do not contain submitted credentials");

            AuthService client = new AuthService(f.db, audit);
            client.initialize();
            check(client.login("client1", GOOD.toCharArray()).isSuccess(), "client can sign in for denied-review test");
            check(audit.search(new AccessControl(client), "", null).isEmpty(),
                    "non-admin direct log review returns no records");
            check(count(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'ACCESS_DENIED'") > 0,
                    "unauthorized log review is audited");
            check(audit.search(null, "", null).isEmpty(), "direct review without an access provider is denied");

            int beforeUpgrade = count(f.db, "SELECT COUNT(*) FROM logs");
            audit.initialize();
            check(count(f.db, "SELECT COUNT(*) FROM logs") == beforeUpgrade && count(f.db, "SELECT COUNT(*) FROM users") == 5,
                    "repeated audit initialization preserves logs and existing application data");
        } finally { f.close(); }
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
        Fixture f = new Fixture();
        try {
            final SqliteSecurityAudit backing = new SqliteSecurityAudit(f.db);
            backing.initialize();
            SecurityAudit unavailableForAdminChanges = new SecurityAudit() {
                public void record(String event, String username, String detail) {
                    if ("USER_ADMIN".equals(event)) { throw new IllegalStateException("audit storage unavailable"); }
                    backing.record(event, username, detail);
                }
                public void record(Connection connection, String event, String username, String detail) {
                    if ("USER_ADMIN".equals(event)) { throw new IllegalStateException("audit storage unavailable"); }
                    backing.record(connection, event, username, detail);
                }
            };
            AuthService auth = new AuthService(f.db, unavailableForAdminChanges);
            auth.initialize();
            check(auth.login("admin1", GOOD.toCharArray()).isSuccess(), "admin can sign in while the audit provider is available for authentication");
            UserAdminService admin = new UserAdminService(auth, new AccessControl(auth));

            UserAdminService.Result result = admin.changeRole(f.id("client2"), "3", GOOD.toCharArray());
            check(!result.isSuccess(), "account change reports failure when its required audit write fails");
            check("2".equals(f.role("client2")), "role change rolls back when its audit record cannot persist");
            check("0".equals(scalar(f.db, "SELECT COUNT(*) FROM logs WHERE event = 'USER_ADMIN'")),
                    "failed account change does not leave a success audit record");
        } finally { f.close(); }
    }

    // ------------------------------------------------------------------ helpers

    private static final class Fixture {
        final Path db; final AuthService auth; final AccessControl access; final UserAdminService admin;
        Fixture() throws Exception {
            db = tempDb();
            auth = new AuthService(db, SecurityAudit.NONE);
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
