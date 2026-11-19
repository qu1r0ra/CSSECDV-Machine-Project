package Controller;

import Model.Logs;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Compatibility facade for the remaining audit-log view. Legacy CRUD methods were deliberately
 * retired: authentication, account administration, catalog, purchase, and history access belong
 * to their validated service providers and must not bypass authorization or transaction policy.
 */
public class SQLite {
    private final SqliteSecurityAudit securityAudit;

    public SQLite() {
        this(Paths.get("database.db"));
    }

    /** Uses the caller-selected database so UI tests and packaged launches share one isolated store. */
    public SQLite(java.nio.file.Path databasePath) {
        securityAudit = new SqliteSecurityAudit(databasePath);
    }

    void bindTrustedAuth(AuthService auth) { securityAudit.bindTrustedAuth(auth); }

    /** The audit provider applies the centralized Role 5 check and parameterized search filters. */
    public ArrayList<Logs> getLogs(AccessControl access, String text, String event) {
        return new ArrayList<Logs>(securityAudit.search(access, text, event));
    }
}
