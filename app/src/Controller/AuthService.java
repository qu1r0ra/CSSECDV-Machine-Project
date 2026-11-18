package Controller;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authentication: registration, login, lockout, re-authentication and the single trusted session.
 * Every public method fails closed: any unexpected error yields a denial with a generic message.
 */
public final class AuthService {
    public static final String GENERIC_LOGIN_FAILURE = "Invalid username and/or password";
    public static final String LOGIN_UNAVAILABLE = "Unable to sign in right now. Please try again later.";
    public static final String REGISTRATION_FAILURE = "Registration could not be completed. Please try a different username or try again later.";

    private static final class Session {
        final int userId; final String username; final int role; final long generation;
        Session(int userId, String username, int role, long generation) {
            this.userId = userId; this.username = username; this.role = role; this.generation = generation;
        }
    }

    private final AccountStore store;
    private final SecurityAudit audit;
    private final AtomicLong generations = new AtomicLong();
    private volatile Session session;
    private volatile Runnable sessionRevoked = new Runnable() { public void run() { } };

    public AuthService(Path database, SecurityAudit audit) {
        if (database == null) { throw new IllegalArgumentException("database is required"); }
        this.store = new AccountStore(database);
        this.audit = audit == null ? SecurityAudit.NONE : audit;
    }

    AccountStore store() { return store; }
    SecurityAudit audit() { return audit; }

    /** Called when the service ends a session on its own (disabled, deleted or role changed while signed in). */
    public void setSessionRevokedListener(Runnable listener) { sessionRevoked = listener == null ? new Runnable() { public void run() { } } : listener; }

    public void initialize() {
        try { store.ensureSchemaAndMigrate(); }
        catch (Exception ex) { throw new IllegalStateException("Account storage is unavailable"); }
    }

    // ---------------------------------------------------------------- registration

    public RegistrationResult register(String username, char[] password, char[] confirm) {
        try {
            if (!InputValidator.isValidUsername(username)) { return rejected(username, InputValidator.USERNAME_RULE); }
            String violation = InputValidator.passwordPolicyViolation(password);
            if (violation != null) { return rejected(username, violation); }
            if (!InputValidator.sameSecret(password, confirm)) { return rejected(username, "Passwords do not match."); }
            String hash = PasswordHasher.hash(password);
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                if (store.findByUsername(c, username) != null) {
                    c.rollback();
                    audit.record("REGISTRATION", username, "rejected: username unavailable");
                    return RegistrationResult.failure(REGISTRATION_FAILURE);
                }
                store.insertClient(c, username, hash);
                c.commit();
            } catch (SQLException ex) {
                audit.record("REGISTRATION", username, AccountStore.isConstraint(ex) ? "rejected: username unavailable" : "failed");
                return RegistrationResult.failure(REGISTRATION_FAILURE);
            }
            audit.record("REGISTRATION", username, "success");
            return RegistrationResult.success();
        } catch (Exception ex) {
            return RegistrationResult.failure(REGISTRATION_FAILURE);
        } finally {
            clear(password); clear(confirm);
        }
    }

    private RegistrationResult rejected(String username, String message) {
        audit.record("VALIDATION_FAILURE", InputValidator.isValidUsername(username) ? username : "UNKNOWN", "registration input rejected");
        return RegistrationResult.failure(message);
    }

    // ---------------------------------------------------------------- login

    public LoginResult login(String username, char[] password) {
        try {
            if (!InputValidator.isValidUsername(username) || !InputValidator.isValidPasswordShape(password)) {
                audit.record("VALIDATION_FAILURE", "UNKNOWN", "login input rejected");
                return LoginResult.failed();
            }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                AccountStore.Account a = store.findByUsername(c, username);
                if (a == null) {
                    PasswordHasher.verifyAgainstDummy(password);
                    audit.record("AUTH_FAILURE", username, "unknown account");
                    return LoginResult.failed();
                }
                boolean matches = PasswordHasher.verify(password, a.passwordHash);
                if (!a.isActive()) {
                    audit.record("AUTH_FAILURE", a.username, "account disabled or locked");
                    return LoginResult.failed();
                }
                String now = Instant.now().toString();
                if (!matches) {
                    boolean disabled = store.recordFailure(c, a.id, now, true);
                    c.commit();
                    audit.record("AUTH_FAILURE", a.username, "invalid password");
                    if (disabled) { audit.record("ACCOUNT_DISABLED", a.username, "role set to 1 after " + AccountStore.MAX_FAILED_ATTEMPTS + " consecutive failures"); }
                    return LoginResult.failed();
                }
                store.recordSuccess(c, a.id, now, PasswordHasher.needsRehash(a.passwordHash) ? PasswordHasher.hash(password) : null);
                c.commit();
                session = new Session(a.id, a.username, a.role, generations.incrementAndGet());
                audit.record("AUTH_SUCCESS", a.username, "login");
                return LoginResult.success(a.username, a.role, format(a.lastAttemptAt), a.lastAttemptStatus, a.failedAttempts);
            }
        } catch (Exception ex) {
            session = null;
            return LoginResult.error();
        } finally {
            clear(password);
        }
    }

    public void logout() { session = null; }

    // ---------------------------------------------------------------- session

    /** Trusted identity, re-checked against the database each time; null when nobody is validly signed in. */
    public BundleBService.Actor currentActor() {
        Session s = session;
        if (s == null) { return null; }
        try (Connection c = store.open()) {
            AccountStore.Account a = store.findById(c, s.userId);
            if (a == null || !a.isActive() || a.role != s.role || !a.username.equals(s.username)) {
                revoke(s);
                return null;
            }
            return new BundleBService.Actor(s.userId, s.username, s.role, s.generation);
        } catch (Exception ex) {
            return null;
        }
    }

    private void revoke(Session s) {
        if (session == s) {
            session = null;
            try { sessionRevoked.run(); } catch (RuntimeException ignored) { }
        }
    }

    static boolean sameSession(BundleBService.Actor a, BundleBService.Actor b) {
        return a != null && b != null && a.getUserId() == b.getUserId() && a.getRole() == b.getRole()
                && a.getUsername().equals(b.getUsername()) && a.getSessionGeneration() == b.getSessionGeneration();
    }

    // ---------------------------------------------------------------- re-authentication

    /**
     * Confirms the signed-in user's password before a critical operation. A wrong password counts toward the
     * same five-failure lockout; reaching it disables the account and ends the session.
     * Throws IllegalStateException when storage is unavailable so callers can fall back safely.
     */
    public boolean verifyCurrentPassword(BundleBService.Actor actor, char[] password) {
        try {
            if (!InputValidator.isValidPasswordShape(password)) {
                audit.record("VALIDATION_FAILURE", actor == null ? "UNKNOWN" : actor.getUsername(), "re-authentication input rejected");
                return false;
            }
            Session s = session;
            BundleBService.Actor now = currentActor();
            if (s == null || !sameSession(actor, now)) { return false; }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                AccountStore.Account a = store.findById(c, s.userId);
                if (a == null || !a.isActive()) { revoke(s); return false; }
                if (PasswordHasher.verify(password, a.passwordHash)) {
                    store.resetFailures(c, a.id);
                    c.commit();
                    return true;
                }
                boolean disabled = store.recordFailure(c, a.id, Instant.now().toString(), false);
                c.commit();
                audit.record("AUTH_FAILURE", a.username, "re-authentication failed");
                if (disabled) {
                    audit.record("ACCOUNT_DISABLED", a.username, "role set to 1 after " + AccountStore.MAX_FAILED_ATTEMPTS + " consecutive failures");
                    revoke(s);
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Verification unavailable");
        } finally {
            clear(password);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String format(String isoInstant) {
        if (isoInstant == null) { return null; }
        try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(Instant.parse(isoInstant));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static void clear(char[] value) { if (value != null) { Arrays.fill(value, '\0'); } }

    // ---------------------------------------------------------------- results

    public static final class LoginResult {
        public enum Status { SUCCESS, FAILED, ERROR }
        private final Status status; private final String username; private final int role;
        private final String previousAt; private final String previousStatus; private final int failedBefore;
        private LoginResult(Status status, String username, int role, String previousAt, String previousStatus, int failedBefore) {
            this.status = status; this.username = username; this.role = role;
            this.previousAt = previousAt; this.previousStatus = previousStatus; this.failedBefore = failedBefore;
        }
        static LoginResult failed() { return new LoginResult(Status.FAILED, null, 0, null, null, 0); }
        static LoginResult error() { return new LoginResult(Status.ERROR, null, 0, null, null, 0); }
        static LoginResult success(String username, int role, String at, String status, int failedBefore) {
            return new LoginResult(Status.SUCCESS, username, role, at, status, failedBefore);
        }
        public boolean isSuccess() { return status == Status.SUCCESS; }
        public Status getStatus() { return status; }
        public String getUsername() { return username; }
        public int getRole() { return role; }
        public String getMessage() {
            return status == Status.ERROR ? LOGIN_UNAVAILABLE : status == Status.FAILED ? GENERIC_LOGIN_FAILURE : "Signed in.";
        }
        /** Timestamp and status of the previous login attempt, for display right after a successful sign-in. */
        public String describePreviousAttempt() {
            if (previousAt == null || previousStatus == null) { return "No previous login attempts are recorded for this account."; }
            String state = "SUCCESS".equals(previousStatus) ? "successful" : "FAILED";
            String text = "Last login attempt: " + previousAt + " - " + state + ".";
            if (failedBefore > 0) { text += "\nFailed attempts since your last successful login: " + failedBefore + "."; }
            return text;
        }
    }

    public static final class RegistrationResult {
        private final boolean success; private final String message;
        private RegistrationResult(boolean success, String message) { this.success = success; this.message = message; }
        static RegistrationResult success() { return new RegistrationResult(true, "Registration successful. You can now log in."); }
        static RegistrationResult failure(String message) { return new RegistrationResult(false, message); }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
