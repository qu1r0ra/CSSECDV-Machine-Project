package Controller;

import java.util.regex.Pattern;

/**
 * Reject-by-default validation for account-related input. Every method either accepts the value
 * exactly as given or rejects it; nothing is trimmed, cleaned or escaped.
 */
public final class InputValidator {
    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 20;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    public static final int ROLE_MIN = 1;
    public static final int ROLE_MAX = 5;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9]{" + USERNAME_MIN + "," + USERNAME_MAX + "}");
    private static final Pattern ROLE = Pattern.compile("[1-5]");

    public static final String USERNAME_RULE = "Username must be " + USERNAME_MIN + "-" + USERNAME_MAX + " letters and digits only.";
    public static final String PASSWORD_RULE = "Password must be " + PASSWORD_MIN + "-" + PASSWORD_MAX
            + " characters with uppercase, lowercase, a digit and a symbol (no spaces).";

    private InputValidator() { }

    public static boolean isValidUsername(String username) {
        return username != null && username.length() <= USERNAME_MAX && USERNAME.matcher(username).matches();
    }

    /** Shape check for a password being presented at login: bounded length, printable ASCII only. No complexity rule. */
    public static boolean isValidPasswordShape(char[] password) {
        if (password == null || password.length < 1 || password.length > PASSWORD_MAX) { return false; }
        for (char c : password) { if (!isPrintableAscii(c)) { return false; } }
        return true;
    }

    /** Returns null when the password satisfies the creation policy, otherwise a non-sensitive reason. */
    public static String passwordPolicyViolation(char[] password) {
        if (password == null || password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) { return PASSWORD_RULE; }
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (char c : password) {
            if (!isPrintableAscii(c)) { return PASSWORD_RULE; }
            if (c >= 'A' && c <= 'Z') { upper = true; }
            else if (c >= 'a' && c <= 'z') { lower = true; }
            else if (c >= '0' && c <= '9') { digit = true; }
            else { symbol = true; }
        }
        return upper && lower && digit && symbol ? null : PASSWORD_RULE;
    }

    /** Strict role parsing: exactly one digit 1-5. */
    public static Integer parseRole(String text) {
        if (text == null || !ROLE.matcher(text).matches()) { return null; }
        return Integer.valueOf(text.charAt(0) - '0');
    }

    public static boolean isValidRole(int role) { return role >= ROLE_MIN && role <= ROLE_MAX; }

    public static boolean isValidId(int id) { return id > 0; }

    /** Comparison that does not exit early on the first difference. */
    public static boolean sameSecret(char[] a, char[] b) {
        if (a == null || b == null) { return false; }
        int diff = a.length ^ b.length;
        int common = Math.min(a.length, b.length);
        for (int i = 0; i < common; i++) { diff |= a[i] ^ b[i]; }
        return diff == 0 && a.length > 0;
    }

    private static boolean isPrintableAscii(char c) { return c >= 0x21 && c <= 0x7E; }
}
