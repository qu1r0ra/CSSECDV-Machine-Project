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
        if (database == null || audit == null) { throw new IllegalArgumentException("database and audit provider are required"); }
        this.store = new AccountStore(database);
        this.audit = audit;
    }

    AccountStore store() { return store; }
    SecurityAudit audit() { return audit; }
    boolean usesDatabase(Path database) { return store.usesDatabase(database); }

    /** Ends a privileged session and notifies the UI when a required security operation fails. */
    void failSafeSession() {
        Session current = session;
        if (current != null) { revoke(current); }
    }

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
                try {
                    if (store.findByUsername(c, username) != null) {
                        audit.record(c, "REGISTRATION", username, "rejected: username unavailable");
                        c.commit();
                        return RegistrationResult.failure(REGISTRATION_FAILURE);
                    }
                    store.insertClient(c, username, hash);
                    audit.record(c, "REGISTRATION", username, "success");
                    c.commit();
                    return RegistrationResult.success();
                } catch (SQLException ex) {
                    rollbackQuietly(c);
                    if (AccountStore.isConstraint(ex)) {
                        recordStandalone("REGISTRATION", username, "rejected: username unavailable");
                    }
                    return RegistrationResult.failure(REGISTRATION_FAILURE);
                } catch (RuntimeException ex) {
                    rollbackQuietly(c);
                    return RegistrationResult.failure(REGISTRATION_FAILURE);
                }
            }
        } catch (Exception ex) {
            return RegistrationResult.failure(REGISTRATION_FAILURE);
        } finally {
            clear(password); clear(confirm);
        }
    }

    private RegistrationResult rejected(String username, String message) {
        recordStandalone("VALIDATION_FAILURE", InputValidator.isValidUsername(username) ? username : "UNKNOWN", "registration input rejected");
        return RegistrationResult.failure(message);
    }

    private void recordStandalone(String event, String username, String detail) {
        try (Connection c = store.open()) {
            c.setAutoCommit(false);
            try {
                audit.record(c, event, username, detail);
                c.commit();
            } catch (RuntimeException ex) {
                rollbackQuietly(c);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Audit record is unavailable.");
        }
    }

    // ---------------------------------------------------------------- login

    public LoginResult login(String username, char[] password) {
        try {
            if (!InputValidator.isValidUsername(username)) {
                audit.record("VALIDATION_FAILURE", "UNKNOWN", "login input rejected");
                return LoginResult.failed();
            }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    AccountStore.Account a = store.findByUsername(c, username);
                    boolean validShape = InputValidator.isValidPasswordShape(password);
                    if (a == null) {
                        if (validShape) { PasswordHasher.verifyAgainstDummy(password); }
                        else { audit.record(c, "VALIDATION_FAILURE", "UNKNOWN", "login password input rejected"); }
                        audit.record(c, "AUTH_FAILURE", username, "unknown account");
                        c.commit();
                        return LoginResult.failed();
                    }
                    if (!a.isActive()) {
                        if (!validShape) { audit.record(c, "VALIDATION_FAILURE", a.username, "login password input rejected"); }
                        audit.record(c, "AUTH_FAILURE", a.username, "account disabled or locked");
                        c.commit();
                        return LoginResult.failed();
                    }
                    if (!validShape) {
                        boolean disabled = store.recordFailure(c, a.id, Instant.now().toString());
                        audit.record(c, "VALIDATION_FAILURE", a.username, "login password input rejected");
                        audit.record(c, "AUTH_FAILURE", a.username, "invalid credentials");
                        if (disabled) { audit.record(c, "ACCOUNT_DISABLED", a.username, "role set to 1 after " + AccountStore.MAX_FAILED_ATTEMPTS + " consecutive failures"); }
                        c.commit();
                        if (disabled) {
                            Session current = session;
                            if (current != null && current.userId == a.id) { revoke(current); }
                        }
                        return LoginResult.failed();
                    }
                    boolean matches = PasswordHasher.verify(password, a.passwordHash);
                    String now = Instant.now().toString();
                    if (!matches) {
                        boolean disabled = store.recordFailure(c, a.id, now);
                        audit.record(c, "AUTH_FAILURE", a.username, "invalid password");
                        if (disabled) { audit.record(c, "ACCOUNT_DISABLED", a.username, "role set to 1 after " + AccountStore.MAX_FAILED_ATTEMPTS + " consecutive failures"); }
                        c.commit();
                        if (disabled) {
                            Session current = session;
                            if (current != null && current.userId == a.id) { revoke(current); }
                        }
                        return LoginResult.failed();
                    }
                    store.recordSuccess(c, a.id, now, PasswordHasher.needsRehash(a.passwordHash) ? PasswordHasher.hash(password) : null);
                    audit.record(c, "AUTH_SUCCESS", a.username, "login");
                    c.commit();
                    session = new Session(a.id, a.username, a.role, generations.incrementAndGet());
                    return LoginResult.success(a.username, a.role, format(a.lastAttemptAt), a.lastAttemptStatus, a.failedAttempts);
                } catch (Exception ex) {
                    rollbackQuietly(c);
                    throw ex;
                }
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
            // A failed identity refresh is an unrecoverable authorization-state error. End the
            // session and let the configured listener route the UI to its safe public state.
            revoke(s);
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
     * Confirms the signed-in user's password before a critical operation. A wrong password increments the
     * separate re-authentication failure counter and is audited; it does not contribute to the five-failed-login
     * lockout or disable the account.
     * Throws IllegalStateException when storage is unavailable so callers can fall back safely.
     */
    public boolean verifyCurrentPassword(BundleBService.Actor actor, char[] password) {
        try {
            if (!InputValidator.isValidPasswordShape(password)) {
                try {
                    recordStandalone("VALIDATION_FAILURE", actor == null ? "UNKNOWN" : actor.getUsername(), "re-authentication input rejected");
                } catch (RuntimeException ex) {
                    failSafeSession();
                    throw new IllegalStateException("Verification unavailable.");
                }
                return false;
            }
            Session s = session;
            BundleBService.Actor now = currentActor();
            if (s == null || !sameSession(actor, now)) { return false; }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    AccountStore.Account a = store.findById(c, s.userId);
                    if (a == null || !a.isActive()) { rollbackQuietly(c); revoke(s); return false; }
                    if (PasswordHasher.verify(password, a.passwordHash)) {
                        store.resetReauthenticationFailures(c, a.id);
                        audit.record(c, "AUTH_SUCCESS", a.username, "re-authentication");
                        c.commit();
                        return true;
                    }
                    store.recordReauthenticationFailure(c, a.id);
                    audit.record(c, "AUTH_FAILURE", a.username, "re-authentication failed");
                    c.commit();
                    return false;
                } catch (SQLException ex) {
                    rollbackQuietly(c);
                    failSafeSession();
                    throw ex;
                } catch (RuntimeException ex) {
                    rollbackQuietly(c);
                    failSafeSession();
                    throw ex;
                }
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

    private static void rollbackQuietly(Connection c) {
        try { c.rollback(); } catch (SQLException ignored) { }
    }

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
