package Controller;

import Model.Logs;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Durable SQLite provider for the shared security-audit interface. */
public final class SqliteSecurityAudit implements SecurityAudit {
    private static final int MAX_SEARCH_LENGTH = 128;
    private static final Pattern EVENT_FILTER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern SEARCH_TEXT = Pattern.compile("[A-Za-z0-9 _:-]*");

    private final String url;
    private final Path database;
    private volatile AuthService trustedAuth;

    public SqliteSecurityAudit(Path database) {
        if (database == null) { throw new IllegalArgumentException("database is required"); }
        this.database = database.toAbsolutePath().normalize();
        url = "jdbc:sqlite:" + this.database.toString();
    }

    /** Binds log review to the application's own session provider and database. */
    synchronized void bindTrustedAuth(AuthService auth) {
        if (auth == null || !auth.usesDatabase(database)) { throw new IllegalArgumentException("authentication provider must use the audit database"); }
        if (trustedAuth != null && trustedAuth != auth) { throw new IllegalStateException("trusted authentication provider is already bound"); }
        trustedAuth = auth;
    }

    /** Additive and repeatable: an existing logs table and its rows are retained. */
    public void initialize() {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                     + "event TEXT NOT NULL, username TEXT NOT NULL, \"desc\" TEXT NOT NULL, timestamp TEXT NOT NULL)")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw unavailable();
        }
    }

    /** Persists standalone events in their own transaction. */
    public void record(String event, String username, String detail) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                record(connection, event, username, detail);
                connection.commit();
            } catch (RuntimeException ex) {
                rollback(connection);
                throw ex;
            }
        } catch (SQLException ex) {
            throw unavailable();
        }
    }

    /** Appends in the caller's transaction so authentication changes and their audit row are atomic. */
    public void record(Connection connection, String event, String username, String detail) {
        if (connection == null) { throw new IllegalArgumentException("connection is required"); }
        if (event == null || !EVENT_FILTER.matcher(event).matches()) { throw new IllegalArgumentException("invalid audit event"); }
        String actor = username == null ? "UNKNOWN" : username;
        String description = detail == null ? "" : detail;
        if (actor.length() > 128 || description.length() > 512) { throw new IllegalArgumentException("audit value too long"); }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logs(event, username, \"desc\", timestamp) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, event);
            statement.setString(2, actor);
            statement.setString(3, description);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw unavailable();
        }
    }

    /**
     * Returns a read-only filtered view after checking the bound application's current Role 5 session.
     * The legacy access argument is ignored so it cannot substitute an identity from another provider.
     */
    public List<Logs> search(AccessControl suppliedAccess, String text, String event) {
        AuthService auth = trustedAuth;
        if (auth == null) {
            recordDeniedWithoutAccess();
            return Collections.emptyList();
        }
        AccessControl access = new AccessControl(auth);
        BundleBService.Actor actor = access.require(AccessControl.Feature.VIEW_LOGS);
        if (actor == null) { return Collections.emptyList(); }

        String term = text == null ? "" : text;
        String eventFilter = event == null ? "" : event;
        if (term.length() > MAX_SEARCH_LENGTH || !SEARCH_TEXT.matcher(term).matches()
                || (!eventFilter.isEmpty() && !EVENT_FILTER.matcher(eventFilter).matches())) {
            try { record("VALIDATION_FAILURE", actor.getUsername(), "audit search filter rejected"); }
            catch (RuntimeException ignored) { }
            return Collections.emptyList();
        }

        String sql = "SELECT id, event, username, \"desc\", timestamp FROM logs "
                + "WHERE (? = '' OR event = ? COLLATE NOCASE) "
                + "AND (? = '' OR instr(lower(event), lower(?)) > 0 "
                + "OR instr(lower(username), lower(?)) > 0 "
                + "OR instr(lower(\"desc\"), lower(?)) > 0 "
                + "OR instr(lower(timestamp), lower(?)) > 0) ORDER BY id DESC";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventFilter);
            statement.setString(2, eventFilter);
            statement.setString(3, term);
            statement.setString(4, term);
            statement.setString(5, term);
            statement.setString(6, term);
            statement.setString(7, term);
            List<Logs> records = new ArrayList<Logs>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    records.add(new Logs(rows.getInt("id"), rows.getString("event"), rows.getString("username"),
                            rows.getString("desc"), rows.getString("timestamp")));
                }
            }
            return Collections.unmodifiableList(records);
        } catch (SQLException ex) {
            throw new IllegalStateException("Security logs are unavailable.");
        }
    }

    private void recordDeniedWithoutAccess() {
        try { record("ACCESS_DENIED", "UNKNOWN", "VIEW_LOGS"); }
        catch (RuntimeException ignored) { }
    }

    private Connection open() throws SQLException { return DriverManager.getConnection(url); }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Security audit storage is unavailable.");
    }
}
