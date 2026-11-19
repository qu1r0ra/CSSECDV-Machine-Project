package Controller;

import java.sql.Connection;

/**
 * Narrow seam for the Logging bundle. Authentication, validation and authorization code reports
 * security-relevant events here; the Logging owner supplies an implementation that writes them to
 * the logs table. Application startup supplies a durable provider; the no-op is reserved for isolated checks.
 */
public interface SecurityAudit {
    SecurityAudit NONE = new SecurityAudit() {
        public void record(String event, String username, String detail) { }
        public void record(Connection connection, String event, String username, String detail) { }
    };

    /** Events used: AUTH_SUCCESS, AUTH_FAILURE, ACCOUNT_DISABLED, VALIDATION_FAILURE, ACCESS_DENIED, REGISTRATION, USER_ADMIN. */
    void record(String event, String username, String detail);

    /** Writes an event in the caller's transaction so related mutations can roll back with it. */
    void record(Connection connection, String event, String username, String detail);
}
