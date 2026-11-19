package Controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Preparation tool (not part of the running application): creates a database and four demo
 * accounts to freshly generated policy-compliant passwords, stored only as salted hashes. Passwords are
 * written to a caller-selected operator-only file; they are never printed to the console or stored in SQLite.
 * Usage: java -cp CSSECDV.jar:lib/sqlite-jdbc-3.23.1.jar Controller.DemoAccountSeeder database.db credentials.txt
 */
public final class DemoAccountSeeder {
    private static final String[][] ACCOUNTS = { {"admin", "5"}, {"manager", "4"}, {"staff", "3"}, {"client", "2"} };
    private static final SecureRandom RANDOM = new SecureRandom();

    private DemoAccountSeeder() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) { System.err.println("Usage: DemoAccountSeeder <database file> <operator credential file>"); System.exit(2); }
        Path db = Paths.get(args[0]);
        Path credentialFile = Paths.get(args[1]);
        if (Files.exists(db)) { throw new IllegalArgumentException("Refusing to overwrite an existing database: " + db); }
        if (credentialFile.toAbsolutePath().normalize().startsWith(Paths.get(".").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Credential output must be outside the project checkout");
        }
        AuthService auth = new AuthService(db, SecurityAudit.NONE);
        auth.initialize();
        AccountStore store = auth.store();
        List<String> credentials = new ArrayList<String>();
        try (Connection c = store.open()) {
            c.setAutoCommit(false);
            for (String[] account : ACCOUNTS) {
                char[] password = generate();
                String hash = PasswordHasher.hash(password);
                store.insertClient(c, account[0], hash);
                AccountStore.Account existing = store.findByUsername(c, account[0]);
                if (existing == null || !store.updateRole(c, existing.id, Integer.parseInt(account[1]))) {
                    throw new IllegalStateException("Could not assign demo role: " + account[0]);
                }
                credentials.add(account[0] + " (role " + account[1] + "): " + new String(password));
                AuthService.clear(password);
            }
            c.commit();
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(credentialFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            writer.println("Operator-only demo credentials. Keep outside source control.");
            for (String line : credentials) { writer.println(line); }
            writer.flush();
            if (writer.checkError()) { throw new IllegalStateException("Could not write operator credential file"); }
        }
        BundleBService business = new BundleBService(db, new AppSecurityGateway(auth, new Runnable() { public void run() { } }));
        business.initialize();
        AuthService.LoginResult login = auth.login("staff", findPassword(credentials, "staff"));
        if (!login.isSuccess()) { throw new IllegalStateException("Could not initialize demo catalog"); }
        char[] staffPassword = findPassword(credentials, "staff");
        try {
            BundleBService.Result<BundleBService.CatalogProduct> antivirus =
                    business.addProduct("Antivirus", "8", "49.99", staffPassword);
            if (!antivirus.isSuccess()) { throw new IllegalStateException("Could not add demo catalog product"); }
        } finally { AuthService.clear(staffPassword); }
        char[] secondProductPassword = findPassword(credentials, "staff");
        try {
            BundleBService.Result<BundleBService.CatalogProduct> firewall =
                    business.addProduct("Firewall", "5", "79.50", secondProductPassword);
            if (!firewall.isSuccess()) { throw new IllegalStateException("Could not add demo catalog product"); }
        } finally { AuthService.clear(secondProductPassword); }
        auth.logout();
        System.out.println("Prepared demo database with four hashed accounts and two products.");
    }

    private static char[] findPassword(List<String> credentials, String username) {
        String prefix = username + " (role ";
        for (String line : credentials) {
            if (line.startsWith(prefix)) { return line.substring(line.indexOf(": ") + 2).toCharArray(); }
        }
        throw new IllegalArgumentException("Missing seeded account");
    }

    static char[] generate() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnopqrstuvwxyz", digits = "23456789", symbols = "!@#$%^&*-_=+";
        String all = upper + lower + digits + symbols;
        char[] out = new char[16];
        out[0] = pick(upper); out[1] = pick(lower); out[2] = pick(digits); out[3] = pick(symbols);
        for (int i = 4; i < out.length; i++) { out[i] = pick(all); }
        for (int i = out.length - 1; i > 0; i--) { int j = RANDOM.nextInt(i + 1); char t = out[i]; out[i] = out[j]; out[j] = t; }
        return out;
    }

    private static char pick(String set) { return set.charAt(RANDOM.nextInt(set.length())); }
}
