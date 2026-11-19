package Controller;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Parameterized access to the users table. Callers own transactions and exception handling. */
final class AccountStore {
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int DISABLED_ROLE = 1;

    static final class Account {
        final int id; final String username; final String passwordHash; final int role; final int locked;
        final int failedAttempts; final String lastAttemptAt; final String lastAttemptStatus;
        Account(int id, String username, String passwordHash, int role, int locked, int failedAttempts,
                String lastAttemptAt, String lastAttemptStatus) {
            this.id = id; this.username = username; this.passwordHash = passwordHash; this.role = role;
            this.locked = locked; this.failedAttempts = failedAttempts;
            this.lastAttemptAt = lastAttemptAt; this.lastAttemptStatus = lastAttemptStatus;
        }
        boolean isActive() { return role >= 2 && role <= 5 && locked == 0; }
    }

    private static final String COLUMNS = "id, username, password, role, locked, failed_attempts, last_attempt_at, last_attempt_status";
    private final String url;

    AccountStore(Path database) { this.url = "jdbc:sqlite:" + database.toAbsolutePath().normalize().toString(); }

    boolean usesDatabase(Path database) {
        return database != null && url.equals("jdbc:sqlite:" + database.toAbsolutePath().normalize().toString());
    }

    Connection open() throws SQLException { return DriverManager.getConnection(url); }

    /** Additive, idempotent schema upgrade; then hashes any legacy plaintext password so none remain stored. */
    void ensureSchemaAndMigrate() throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, role INTEGER DEFAULT 2, locked INTEGER DEFAULT 0)")) {
                s.execute();
            }
            addColumnIfMissing(c, "failed_attempts", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "reauth_failures", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "last_attempt_at", "TEXT");
            addColumnIfMissing(c, "last_attempt_status", "TEXT");
            c.commit();
            try (PreparedStatement s = c.prepareStatement("CREATE UNIQUE INDEX IF NOT EXISTS users_username_nocase ON users(username COLLATE NOCASE)")) {
                s.execute();
                c.commit();
            } catch (SQLException ignored) {
                c.rollback(); // pre-existing case-variant duplicates; registration still checks case-insensitively
            }
            List<int[]> ids = new ArrayList<int[]>();
            List<String> legacy = new ArrayList<String>();
            try (PreparedStatement s = c.prepareStatement("SELECT id, password FROM users");
                 ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    String stored = rs.getString(2);
                    if (!PasswordHasher.isHashFormat(stored)) { ids.add(new int[] {rs.getInt(1)}); legacy.add(stored); }
                }
            }
            try (PreparedStatement u = c.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
                for (int i = 0; i < ids.size(); i++) {
                    u.setString(1, PasswordHasher.hash(legacy.get(i) == null ? new char[0] : legacy.get(i).toCharArray()));
                    u.setInt(2, ids.get(i)[0]);
                    u.executeUpdate();
                }
            }
            c.commit();
        }
    }

    private static void addColumnIfMissing(Connection c, String column, String definition) throws SQLException {
        boolean present = false;
        try (PreparedStatement s = c.prepareStatement("PRAGMA table_info(users)"); ResultSet rs = s.executeQuery()) {
            while (rs.next()) { if (column.equalsIgnoreCase(rs.getString("name"))) { present = true; break; } }
        }
        if (!present) {
            try (PreparedStatement s = c.prepareStatement("ALTER TABLE users ADD COLUMN " + column + " " + definition)) { s.execute(); }
        }
    }

    Account findByUsername(Connection c, String username) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE username = ? COLLATE NOCASE")) {
            s.setString(1, username);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? read(rs) : null; }
        }
    }

    Account findById(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE id = ?")) {
            s.setInt(1, id);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? read(rs) : null; }
        }
    }

    List<Account> findAll(Connection c) throws SQLException {
        List<Account> list = new ArrayList<Account>();
        try (PreparedStatement s = c.prepareStatement("SELECT " + COLUMNS + " FROM users ORDER BY id");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) { list.add(read(rs)); }
        }
        return list;
    }

    private static Account read(ResultSet rs) throws SQLException {
        return new Account(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getInt("role"),
                rs.getInt("locked"), rs.getInt("failed_attempts"), rs.getString("last_attempt_at"), rs.getString("last_attempt_status"));
    }

    /** New self-registered accounts always receive the Client role; the role is never taken from input. */
    void insertClient(Connection c, String username, String hash) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO users(username, password, role, locked, failed_attempts) VALUES(?, ?, 2, 0, 0)")) {
            s.setString(1, username);
            s.setString(2, hash);
            s.executeUpdate();
        }
    }

    void recordSuccess(Connection c, int id, String now, String newHashOrNull) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET failed_attempts = 0, last_attempt_at = ?, last_attempt_status = 'SUCCESS'"
                + (newHashOrNull == null ? "" : ", password = ?") + " WHERE id = ?")) {
            int i = 1;
            s.setString(i++, now);
            if (newHashOrNull != null) { s.setString(i++, newHashOrNull); }
            s.setInt(i, id);
            s.executeUpdate();
        }
    }

    /** Counts one consecutive failed login; the fifth moves an active account to Role 1. */
    boolean recordFailure(Connection c, int id, String now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET failed_attempts = failed_attempts + 1, last_attempt_at = ?, last_attempt_status = 'FAILED' WHERE id = ? AND role BETWEEN 2 AND 5")) {
            s.setString(1, now);
            s.setInt(2, id);
            s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET role = ? WHERE id = ? AND failed_attempts >= ? AND role >= 2")) {
            s.setInt(1, DISABLED_ROLE);
            s.setInt(2, id);
            s.setInt(3, MAX_FAILED_ATTEMPTS);
            return s.executeUpdate() == 1;
        }
    }

    /** Re-authentication is audited separately and never contributes to the login lockout threshold. */
    void recordReauthenticationFailure(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE users SET reauth_failures = reauth_failures + 1 WHERE id = ? AND role BETWEEN 2 AND 5")) {
            s.setInt(1, id);
            s.executeUpdate();
        }
    }

    void resetReauthenticationFailures(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET reauth_failures = 0 WHERE id = ?")) {
            s.setInt(1, id);
            s.executeUpdate();
        }
    }

    void resetFailures(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET failed_attempts = 0 WHERE id = ?")) {
            s.setInt(1, id);
            s.executeUpdate();
        }
    }

    boolean updateRole(Connection c, int id, int role) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET role = ?, failed_attempts = 0, reauth_failures = 0 WHERE id = ?")) {
            s.setInt(1, role);
            s.setInt(2, id);
            return s.executeUpdate() == 1;
        }
    }

    boolean updateLocked(Connection c, int id, boolean locked) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET locked = ?, failed_attempts = CASE WHEN ? = 0 THEN 0 ELSE failed_attempts END WHERE id = ?")) {
            s.setInt(1, locked ? 1 : 0);
            s.setInt(2, locked ? 1 : 0);
            s.setInt(3, id);
            return s.executeUpdate() == 1;
        }
    }

    boolean updatePassword(Connection c, int id, String hash) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE users SET password = ?, failed_attempts = 0, reauth_failures = 0 WHERE id = ?")) {
            s.setString(1, hash);
            s.setInt(2, id);
            return s.executeUpdate() == 1;
        }
    }

    boolean delete(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            s.setInt(1, id);
            return s.executeUpdate() == 1;
        }
    }

    static boolean isConstraint(SQLException ex) {
        for (SQLException cur = ex; cur != null; cur = cur.getNextException()) { if (cur.getErrorCode() == 19) { return true; } }
        return false;
    }
}
