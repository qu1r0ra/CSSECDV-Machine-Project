package Controller;

/**
 * Narrow seam for the Logging bundle. Authentication, validation and authorization code reports
 * security-relevant events here; the Logging owner supplies an implementation that writes them to
 * the logs table. The default does nothing so this bundle has no dependency on log storage.
 */
public interface SecurityAudit {
    SecurityAudit NONE = new SecurityAudit() {
        public void record(String event, String username, String detail) { }
    };

    /** Events used: AUTH_SUCCESS, AUTH_FAILURE, ACCOUNT_DISABLED, VALIDATION_FAILURE, ACCESS_DENIED, REGISTRATION, USER_ADMIN. */
    void record(String event, String username, String detail);
}
