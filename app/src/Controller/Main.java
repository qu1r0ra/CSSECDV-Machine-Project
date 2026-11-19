package Controller;

import View.Frame;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public SQLite sqlite;
    public BundleBService bundleBService;
    public AuthService auth;
    public AccessControl access;
    public UserAdminService userAdmin;
    private Frame frame;

    public static void main(String[] args) {
        new Main().init();
    }

    public void init() {
        init(Paths.get("database.db"));
    }

    /** Initializes the application against the caller-selected database. */
    public void init(Path databasePath) {
        sqlite = new SQLite(databasePath);
        SqliteSecurityAudit audit = new SqliteSecurityAudit(databasePath);
        auth = new AuthService(databasePath, audit);
        audit.bindTrustedAuth(auth);
        sqlite.bindTrustedAuth(auth);
        try { audit.initialize(); }
        catch (IllegalStateException ignored) { /* Required audit writes fail closed if storage is unavailable. */ }
        try { auth.initialize(); }
        catch (IllegalStateException ignored) { /* Login then fails closed with a generic message. */ }
        access = new AccessControl(auth);
        userAdmin = new UserAdminService(auth, access);
        auth.setSessionRevokedListener(new Runnable() {
            public void run() { if (frame != null) { frame.showSafeUnprivilegedState(); } }
        });
        bundleBService = new BundleBService(databasePath, new AppSecurityGateway(auth, new Runnable() {
            public void run() { if (frame != null) { frame.showSafeUnprivilegedState(); } }
        }));
        try { bundleBService.initialize(); }
        catch (IllegalStateException ignored) { /* B remains unavailable and protected actions fail closed. */ }

        frame = new Frame();
        frame.init(this);
    }

    public Frame getFrame() { return frame; }
}
