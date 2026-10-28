package Controller;

import Model.User;

/**
 * Single site-wide authorization component (checklist 2.2.1-2.2.4).
 *
 * Every panel/action that is restricted to a role must call {@link #check}
 * before performing the action or showing the content. On any ambiguity
 * (null user, unknown role) access is denied: authorization fails securely.
 */
public class Authorization {

    public static final int ROLE_DISABLED = 1;
    public static final int ROLE_CLIENT = 2;
    public static final int ROLE_STAFF = 3;
    public static final int ROLE_MANAGER = 4;
    public static final int ROLE_ADMIN = 5;

    private final SQLite sqlite;

    public Authorization(SQLite sqlite) {
        this.sqlite = sqlite;
    }

    /**
     * Returns true only if the user is authenticated, not disabled, and
     * holds exactly one of the allowed roles. Logs every denial as an
     * access-control violation (checklist 2.5.4) and every ambiguous/error
     * case defaults to denial (checklist 2.2.3).
     */
    public boolean check(User user, String resource, int... allowedRoles) {
        if (user == null || allowedRoles == null) {
            logViolation("UNKNOWN", resource, -1);
            return false;
        }

        int role = user.getRole();
        if (role == ROLE_DISABLED) {
            logViolation(user.getUsername(), resource, role);
            return false;
        }

        for (int allowed : allowedRoles) {
            if (role == allowed) {
                return true;
            }
        }

        logViolation(user.getUsername(), resource, role);
        return false;
    }

    public boolean isValidRoleCode(int role) {
        return role >= ROLE_DISABLED && role <= ROLE_ADMIN;
    }

    private void logViolation(String username, String resource, int role) {
        try {
            if (sqlite != null) {
                sqlite.addLogs("ACCESS_DENIED", username,
                        "Attempted to access '" + resource + "' without sufficient privilege (role=" + role + ")");
            }
        } catch (Exception ex) {
            // Logging must never crash the authorization check itself.
        }
    }
}
