package Controller;

import View.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import org.assertj.swing.core.matcher.JButtonMatcher;
import org.assertj.swing.fixture.FrameFixture;

/** Real Robot driven smoke journeys against the production A/B/C providers and a private SQLite database. */
public final class SwingAcceptanceTest {
    private static final String PASSWORD = "GoodPass1!";
    private final Path database;
    private final Map<String, String> passwords;
    private final Main application = new Main();
    private FrameFixture gui;

    private SwingAcceptanceTest(Path database, Map<String, String> passwords) {
        this.database = database;
        this.passwords = passwords;
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("AssertJ Swing requires a real desktop display; headless mode is unsupported.");
        }
        if (args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException("Usage: SwingAcceptanceTest [prepared database operator credentials]");
        }
        boolean preparedDistribution = args.length == 2;
        Path directory = null;
        Path database = null;
        Map<String, String> passwords = null;
        int exitCode = 0;
        try {
            directory = preparedDistribution ? null : Files.createTempDirectory("secure-app-ui-");
            database = preparedDistribution ? Paths.get(args[0]).toAbsolutePath() : directory.resolve("ui-tests.db");
            passwords = preparedDistribution ? readCredentials(Paths.get(args[1])) : testPasswords();
            if (preparedDistribution) { addLockoutAccount(database); }
            else { prepare(database); }
            new SwingAcceptanceTest(database, passwords).run();
            System.out.println("PASS: registration, masked passwords, role navigation, denied operation, purchase confirmation, lockout, and Admin audit review");
        } catch (Throwable failure) {
            failure.printStackTrace();
            exitCode = 1;
        } finally {
            try {
                if (directory != null) {
                try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                    files.forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (Exception ex) { throw new RuntimeException("Unable to remove isolated UI test artifact " + path, ex); }
                    });
                }
                Files.deleteIfExists(directory);
                }
            } catch (Exception cleanupFailure) {
                cleanupFailure.printStackTrace();
                exitCode = 1;
            }
        }
        System.exit(exitCode);
    }

    private static Map<String, String> testPasswords() {
        Map<String, String> passwords = new LinkedHashMap<String, String>();
        passwords.put("admin", PASSWORD); passwords.put("manager", PASSWORD);
        passwords.put("staff", PASSWORD); passwords.put("client", PASSWORD);
        return passwords;
    }

    private static Map<String, String> readCredentials(Path credentialFile) throws Exception {
        Map<String, String> passwords = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(credentialFile, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String username : new String[] {"admin", "manager", "staff", "client"}) {
                String prefix = username + " (role ";
                if (line.startsWith(prefix) && line.contains("): ")) {
                    passwords.put(username, line.substring(line.indexOf(": ") + 2));
                }
            }
        }
        if (passwords.size() != 4) { throw new IllegalArgumentException("Operator credential file must include all four demo accounts"); }
        return passwords;
    }

    private static void prepare(Path database) throws Exception {
        SqliteSecurityAudit audit = new SqliteSecurityAudit(database);
        audit.initialize();
        AuthService auth = new AuthService(database, audit);
        auth.initialize();
        AccountStore store = auth.store();
        try (Connection connection = store.open()) {
            connection.setAutoCommit(false);
            addAccount(store, connection, "admin", 5);
            addAccount(store, connection, "manager", 4);
            addAccount(store, connection, "staff", 3);
            addAccount(store, connection, "client", 2);
            addAccount(store, connection, "lockout", 3);
            connection.commit();
        }
        AccessControl access = new AccessControl(auth);
        BundleBService business = new BundleBService(database, new AppSecurityGateway(auth, new Runnable() { public void run() { } }));
        business.initialize();
        if (!auth.login("staff", PASSWORD.toCharArray()).isSuccess()) { throw new AssertionError("test staff login"); }
        if (!business.addProduct("Antivirus", "3", "9.99", PASSWORD.toCharArray()).isSuccess()) {
            throw new AssertionError("test catalog seed");
        }
        auth.logout();
    }

    private static void addLockoutAccount(Path database) throws Exception {
        AuthService auth = new AuthService(database, SecurityAudit.NONE);
        auth.initialize();
        try (Connection connection = auth.store().open()) {
            connection.setAutoCommit(false);
            addAccount(auth.store(), connection, "lockout", 3);
            connection.commit();
        }
    }

    private static void addAccount(AccountStore store, Connection connection, String username, int role) throws Exception {
        store.insertClient(connection, username, PasswordHasher.hash(PASSWORD.toCharArray()));
        AccountStore.Account account = store.findByUsername(connection, username);
        if (!store.updateRole(connection, account.id, role)) { throw new AssertionError("test role seed"); }
    }

    private void run() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { application.init(database); } });
        gui = new FrameFixture(application.getFrame());
        gui.target().setAlwaysOnTop(true);
        gui.show().moveToFront();
        try {
            maskedPasswordsAndRegistration();
            deniedActionAndConfirmation();
            roleNavigation("client", "clientHomeButton");
            roleNavigation("manager", "managerHomeButton");
            roleNavigation("staff", "staffHomeButton");
            roleNavigation("admin", "adminHomeButton");
            adminAuditReview();
            unexpectedUiFailureReturnsToLogin();
            safeFallbackOnDisabledSession();
            exactLockout();
        } finally {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    for (Window window : Window.getWindows()) { window.dispose(); }
                }
            });
            gui.cleanUp();
        }
    }

    private void maskedPasswordsAndRegistration() {
        require(gui.textBox("loginPassword").target() instanceof JPasswordField, "login password is masked control");
        require(((JPasswordField) gui.textBox("loginPassword").target()).getEchoChar() != 0, "login password echo mask");
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { gui.button("openRegistrationButton").target().doClick(); }
            });
        } catch (Exception ex) { throw new AssertionError("registration navigation", ex); }
        gui.robot().waitForIdle();
        require(gui.textBox("registerPassword").target() instanceof JPasswordField, "registration password is masked");
        require(((JPasswordField) gui.textBox("registerPassword").target()).getEchoChar() != 0, "registration password echo mask");
        require(((JPasswordField) gui.textBox("registerConfirmPassword").target()).getEchoChar() != 0, "confirmation password echo mask");
        gui.textBox("registerUsername").setText("newclient");
        gui.textBox("registerPassword").setText(PASSWORD);
        gui.textBox("registerConfirmPassword").setText(PASSWORD);
        gui.button("registerButton").click();
        acceptDialog();
        gui.textBox("loginUsername").setText("newclient");
        gui.textBox("loginPassword").setText("WrongPass1!");
        gui.button("loginButton").click();
        gui.dialog().optionPane().requireMessage(AuthService.GENERIC_LOGIN_FAILURE);
        acceptDialog();
        gui.textBox("loginUsername").setText("newclient");
        gui.textBox("loginPassword").setText(PASSWORD);
        gui.button("loginButton").click();
        String previousAttempt = String.valueOf(gui.dialog().optionPane().target().getMessage());
        require(previousAttempt.contains("Last login attempt:") && previousAttempt.contains("FAILED"),
                "successful login reports the previous failed attempt timestamp and status");
        acceptDialog();
        require(componentVisible("clientHomeButton"), "client role navigation is visible");
        require(!componentVisible("managerHomeButton"), "manager role navigation is hidden from client");
        require(!componentVisible("adminHomeButton"), "admin role navigation is hidden from client");
    }

    private void deniedActionAndConfirmation() throws Exception {
        BundleBService.Result<BundleBService.CatalogProduct> denied = application.bundleBService.addProduct(
                "Denied", "1", "1.00", PASSWORD.toCharArray());
        require(denied.getStatus() == BundleBService.Status.DENIED, "client direct product mutation denied by actual provider");
        gui.button("clientHomeButton").click();
        gui.button("productsNavButton").click();
        require(!componentVisible("addProductButton") && !componentVisible("editProductButton")
                && !componentVisible("deleteProductButton"), "client UI hides product mutation controls");
        gui.table("productTable").selectRows(0);
        gui.button("purchaseButton").click();
        acceptDialog();
        require("CONFIRM PURCHASE".equals(gui.dialog().target().getTitle()), "explicit purchase confirmation is shown");
        acceptDialog();
        acceptDialog();
        require(scalar("SELECT COUNT(*) FROM purchase_records") == 1, "purchase confirmation created one durable history record");
        gui.button("logoutButton").click();
    }

    private void roleNavigation(String username, String roleButton) {
        login(username, passwords.get(username));
        require(componentVisible(roleButton), username + " role navigation is visible");
        String expectedHome = username + "HomePnl";
        require(componentVisible(expectedHome), username + " is routed to the actual " + expectedHome + " panel");
        for (String other : new String[] {"adminHomeButton", "managerHomeButton", "staffHomeButton", "clientHomeButton"}) {
            if (!other.equals(roleButton)) { require(!componentVisible(other), username + " cannot see " + other); }
        }
        for (String otherHome : new String[] {"adminHomePnl", "managerHomePnl", "staffHomePnl", "clientHomePnl"}) {
            if (!otherHome.equals(expectedHome)) { require(!componentVisible(otherHome), username + " is not routed to " + otherHome); }
        }
        gui.button("logoutButton").click();
    }

    private void adminAuditReview() {
        login("admin", passwords.get("admin"));
        gui.button("adminHomeButton").click();
        gui.button("logsNavButton").click();
        int allRows = gui.table("auditTable").rowCount();
        require(allRows >= 3, "Admin sees timestamped records from integrated providers");
        gui.textBox("auditSearchField").setText("REGISTRATION");
        gui.button("auditSearchButton").click();
        gui.table("auditTable").requireRowCount(1);
        gui.button("auditShowAllButton").click();
        gui.table("auditTable").requireRowCount(allRows);
        gui.button("logoutButton").click();
    }

    private void unexpectedUiFailureReturnsToLogin() throws Exception {
        login("admin", passwords.get("admin"));
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { throw new IllegalStateException("injected internal failure"); }
        });
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { } });
        require(application.auth.currentActor() == null, "unexpected UI failure revokes the active session");
        require(componentVisible("loginButton") && !componentVisible("adminHomeButton"),
                "unexpected UI failure returns to Login");
        gui.dialog().optionPane().requireMessage("The request could not be completed. Please sign in again.");
        acceptDialog();
    }

    private void safeFallbackOnDisabledSession() throws Exception {
        login("client", passwords.get("client"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement("UPDATE users SET role = 1 WHERE username = ?")) {
            update.setString(1, "client");
            require(update.executeUpdate() == 1, "test account is disabled in the isolated UI database");
        }
        AtomicReference<Boolean> revoked = new AtomicReference<Boolean>(Boolean.FALSE);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() { revoked.set(Boolean.valueOf(application.auth.currentActor() == null)); }
        });
        require(revoked.get().booleanValue(), "disabled account session is revoked");
        require(componentVisible("loginButton") && !componentVisible("clientHomeButton"),
                "revoked session returns to the unprivileged Login panel");
        require(scalar("SELECT role FROM users WHERE username = 'client'") == 1,
                "test leaves only the isolated database with a disabled account");
    }

    private void exactLockout() throws Exception {
        for (int failure = 1; failure <= 5; failure++) {
            gui.textBox("loginUsername").setText("lockout");
            gui.textBox("loginPassword").setText("WrongPass1!");
            gui.button("loginButton").click();
            gui.dialog().optionPane().requireMessage("Invalid username and/or password");
            acceptDialog();
            int expectedRole = failure < 5 ? 3 : 1;
            require(scalar("SELECT role FROM users WHERE username = 'lockout'") == expectedRole,
                    "account remains active through failure 4 and disables exactly at failure 5");
        }
    }

    private void login(String username, String password) {
        gui.textBox("loginUsername").setText(username);
        gui.textBox("loginPassword").setText(password);
        gui.button("loginButton").click();
        acceptDialog();
    }

    private long scalar(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) { throw new AssertionError("query returned no row: " + sql); }
            return rows.getLong(1);
        }
    }

    private boolean componentVisible(String name) {
        AtomicReference<Boolean> visible = new AtomicReference<Boolean>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    AtomicReference<Boolean> found = new AtomicReference<Boolean>(Boolean.FALSE);
                    visible.set(Boolean.valueOf(findVisibleNamed(gui.target(), name, found)));
                    if (!found.get().booleanValue()) { throw new AssertionError("component not found: " + name); }
                }
            });
        } catch (Exception ex) { throw new AssertionError("checking component visibility: " + name, ex); }
        return visible.get().booleanValue();
    }

    private void acceptDialog() {
        javax.swing.JButton button = gui.dialog().button(JButtonMatcher.withText("OK")).target();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { button.doClick(0); }
            });
        } catch (Exception ex) { throw new AssertionError("accepting the current UI dialog", ex); }
    }

    private static boolean findVisibleNamed(Component root, String name, AtomicReference<Boolean> found) {
        boolean visible = false;
        if (name.equals(root.getName())) {
            found.set(Boolean.TRUE);
            visible = root.isShowing();
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                visible = findVisibleNamed(child, name, found) || visible;
            }
        }
        return visible;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
