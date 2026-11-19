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
        Main application = new Main();
        application.init();
        if (application.getFrame() == null) {
            javax.swing.JOptionPane.showMessageDialog(null, "The application could not be started.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    public void init() {
        init(Paths.get("database.db"));
    }

    /** Initializes the application against the caller-selected database. */
    public void init(Path databasePath) {
        try {
            initialize(databasePath);
            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(new java.awt.EventQueue() {
                @Override protected void dispatchEvent(java.awt.AWTEvent event) {
                    try { super.dispatchEvent(event); }
                    catch (RuntimeException ex) { handleUnexpectedUiFailure(); }
                }
            });
        } catch (RuntimeException ex) {
            if (auth != null) { auth.logout(); }
            if (frame != null) { frame.dispose(); frame = null; }
        }
    }

    private void handleUnexpectedUiFailure() {
        if (auth != null) { auth.logout(); }
        try {
            if (frame != null) { frame.showSafeUnprivilegedState(); }
            javax.swing.JOptionPane pane = new javax.swing.JOptionPane(
                    "The request could not be completed. Please sign in again.", javax.swing.JOptionPane.ERROR_MESSAGE);
            javax.swing.JDialog dialog = pane.createDialog(frame, "Error");
            dialog.setModal(false);
            dialog.setVisible(true);
        } catch (RuntimeException ex) {
            if (frame != null) { frame.dispose(); frame = null; }
        }
    }

    private void initialize(Path databasePath) {
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
