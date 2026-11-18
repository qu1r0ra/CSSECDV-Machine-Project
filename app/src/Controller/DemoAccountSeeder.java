package Controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;

/**
 * Preparation tool (not part of the running application): upgrades a database and sets the five demo
 * accounts to freshly generated policy-compliant passwords, stored only as salted hashes. The generated
 * passwords are printed once so the team can record them for the demo.
 * Usage: java -cp CSSECDV.jar:lib/sqlite-jdbc-3.23.1.jar Controller.DemoAccountSeeder path/to/database.db
 */
public final class DemoAccountSeeder {
    private static final String[][] ACCOUNTS = {
        {"admin", "5"}, {"manager", "4"}, {"staff", "3"}, {"client1", "2"}, {"client2", "2"}
    };
    private static final SecureRandom RANDOM = new SecureRandom();

    private DemoAccountSeeder() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) { System.err.println("Usage: DemoAccountSeeder <database file>"); System.exit(2); }
        Path db = Paths.get(args[0]);
        AuthService auth = new AuthService(db, SecurityAudit.NONE);
        auth.initialize();
        AccountStore store = auth.store();
        try (Connection c = store.open()) {
            c.setAutoCommit(false);
            for (String[] account : ACCOUNTS) {
                char[] password = generate();
                String hash = PasswordHasher.hash(password);
                AccountStore.Account existing = store.findByUsername(c, account[0]);
                if (existing == null) { store.insertClient(c, account[0], hash); existing = store.findByUsername(c, account[0]); }
                store.updatePassword(c, existing.id, hash);
                store.updateRole(c, existing.id, Integer.parseInt(account[1]));
                store.updateLocked(c, existing.id, false);
                System.out.println(account[0] + " (role " + account[1] + ")  password: " + new String(password));
                AuthService.clear(password);
            }
            c.commit();
        }
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
