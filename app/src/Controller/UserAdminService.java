package Controller;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Administrator-only user management. Each operation is authorized through AccessControl, validates its
 * input strictly, and (for role changes, deletion and password changes) re-verifies the administrator's
 * own password immediately beforehand.
 */
public final class UserAdminService {
    private static final String DENIED = "This action is not available for the current account.";
    private static final String INVALID = "Please check the entered information.";
    private static final String FAILED = "The action could not be completed.";
    private static final String REAUTH = "Password verification failed or was cancelled.";

    private final AuthService auth;
    private final AccessControl access;
    private final AccountStore store;

    public UserAdminService(AuthService auth, AccessControl access) {
        this.auth = auth; this.access = access; this.store = auth.store();
    }

    public static final class UserRow {
        private final int id; private final String username; private final int role; private final int locked;
        UserRow(int id, String username, int role, int locked) { this.id = id; this.username = username; this.role = role; this.locked = locked; }
        public int getId() { return id; }
        public String getUsername() { return username; }
        public int getRole() { return role; }
        public int getLocked() { return locked; }
    }

    public static final class Result {
        private final boolean success; private final String message;
        Result(boolean success, String message) { this.success = success; this.message = message; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /** Password hashes are never exposed to the view. */
    public List<UserRow> listUsers() {
        if (access.require(AccessControl.Feature.MANAGE_USERS) == null) { return Collections.emptyList(); }
        try (Connection c = store.open()) {
            List<UserRow> rows = new ArrayList<UserRow>();
            for (AccountStore.Account a : store.findAll(c)) { rows.add(new UserRow(a.id, a.username, a.role, a.locked)); }
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Result changeRole(int userId, String roleText, char[] adminPassword) {
        try {
            BundleBService.Actor admin = access.require(AccessControl.Feature.MANAGE_USERS);
            if (admin == null) { return new Result(false, DENIED); }
            Integer role = InputValidator.parseRole(roleText);
            if (!InputValidator.isValidId(userId) || role == null || userId == admin.getUserId()) { return invalid(admin, "role change"); }
            if (!auth.verifyCurrentPassword(admin, adminPassword)) { return new Result(false, REAUTH); }
            if (!stillSame(admin)) { return new Result(false, DENIED); }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    boolean ok = store.updateRole(c, userId, role.intValue());
                    audit(c, admin, "role change user " + userId + " -> " + role + (ok ? "" : " (not found)"));
                    c.commit();
                    return ok ? new Result(true, "Role updated.") : new Result(false, FAILED);
                } catch (Exception ex) {
                    rollback(c);
                    auth.failSafeSession();
                    return new Result(false, FAILED);
                }
            }
        } catch (Exception ex) {
            return new Result(false, FAILED);
        } finally { AuthService.clear(adminPassword); }
    }

    public Result setLocked(int userId, boolean locked) {
        try {
            BundleBService.Actor admin = access.require(AccessControl.Feature.MANAGE_USERS);
            if (admin == null) { return new Result(false, DENIED); }
            if (!InputValidator.isValidId(userId) || userId == admin.getUserId()) { return invalid(admin, "lock change"); }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    boolean ok = store.updateLocked(c, userId, locked);
                    audit(c, admin, (locked ? "lock" : "unlock") + " user " + userId + (ok ? "" : " (not found)"));
                    c.commit();
                    return ok ? new Result(true, locked ? "Account locked." : "Account unlocked.") : new Result(false, FAILED);
                } catch (Exception ex) {
                    rollback(c);
                    auth.failSafeSession();
                    return new Result(false, FAILED);
                }
            }
        } catch (Exception ex) {
            return new Result(false, FAILED);
        }
    }

    public Result deleteUser(int userId, char[] adminPassword) {
        try {
            BundleBService.Actor admin = access.require(AccessControl.Feature.MANAGE_USERS);
            if (admin == null) { return new Result(false, DENIED); }
            if (!InputValidator.isValidId(userId) || userId == admin.getUserId()) { return invalid(admin, "delete"); }
            if (!auth.verifyCurrentPassword(admin, adminPassword)) { return new Result(false, REAUTH); }
            if (!stillSame(admin)) { return new Result(false, DENIED); }
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    boolean ok = store.delete(c, userId);
                    audit(c, admin, "delete user " + userId + (ok ? "" : " (not found)"));
                    c.commit();
                    return ok ? new Result(true, "User deleted.") : new Result(false, FAILED);
                } catch (Exception ex) {
                    rollback(c);
                    auth.failSafeSession();
                    return new Result(false, FAILED);
                }
            }
        } catch (Exception ex) {
            return new Result(false, FAILED);
        } finally { AuthService.clear(adminPassword); }
    }

    public Result changePassword(int userId, char[] newPassword, char[] confirm, char[] adminPassword) {
        try {
            BundleBService.Actor admin = access.require(AccessControl.Feature.MANAGE_USERS);
            if (admin == null) { return new Result(false, DENIED); }
            if (!InputValidator.isValidId(userId)) { return invalid(admin, "password change"); }
            String violation = InputValidator.passwordPolicyViolation(newPassword);
            if (violation != null) { validationFailure(admin, "password change"); return new Result(false, violation); }
            if (!InputValidator.sameSecret(newPassword, confirm)) {
                validationFailure(admin, "password confirmation mismatch");
                return new Result(false, "Passwords do not match.");
            }
            if (!auth.verifyCurrentPassword(admin, adminPassword)) { return new Result(false, REAUTH); }
            if (!stillSame(admin)) { return new Result(false, DENIED); }
            String hash = PasswordHasher.hash(newPassword);
            try (Connection c = store.open()) {
                c.setAutoCommit(false);
                try {
                    boolean ok = store.updatePassword(c, userId, hash);
                    audit(c, admin, "password change user " + userId + (ok ? "" : " (not found)"));
                    c.commit();
                    return ok ? new Result(true, "Password updated.") : new Result(false, FAILED);
                } catch (Exception ex) {
                    rollback(c);
                    auth.failSafeSession();
                    return new Result(false, FAILED);
                }
            }
        } catch (Exception ex) {
            return new Result(false, FAILED);
        } finally { AuthService.clear(newPassword); AuthService.clear(confirm); AuthService.clear(adminPassword); }
    }

    private boolean stillSame(BundleBService.Actor admin) {
        return AuthService.sameSession(admin, access.require(AccessControl.Feature.MANAGE_USERS));
    }

    private Result invalid(BundleBService.Actor admin, String what) {
        validationFailure(admin, what);
        return new Result(false, INVALID);
    }

    private void validationFailure(BundleBService.Actor admin, String what) {
        try {
            auth.audit().record("VALIDATION_FAILURE", admin.getUsername(), what + " input rejected");
        } catch (RuntimeException ex) {
            auth.failSafeSession();
            throw new IllegalStateException(FAILED);
        }
    }

    private void audit(Connection connection, BundleBService.Actor admin, String detail) {
        auth.audit().record(connection, "USER_ADMIN", admin.getUsername(), detail);
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (java.sql.SQLException ignored) { }
    }
}
