package Controller;

import java.util.regex.Pattern;

/**
 * Shared "reject-by-default" input validation (checklist 2.3.1-2.3.3).
 *
 * Every method here returns a boolean verdict only. None of them modify,
 * trim, or otherwise "clean" the input: sanitization is prohibited by the
 * checklist, so callers must reject the whole input on a false result
 * rather than attempt to repair it.
 */
public final class Validator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern PASSWORD_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern PASSWORD_LOWER = Pattern.compile("[a-z]");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern PASSWORD_SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 32;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 64;
    public static final int PRODUCT_NAME_MAX_LENGTH = 64;
    public static final int ROLE_MIN = 1;
    public static final int ROLE_MAX = 5;

    private Validator() {
    }

    public static boolean isNonNull(String value) {
        return value != null;
    }

    public static boolean isWithinLength(String value, int minLength, int maxLength) {
        if (value == null) {
            return false;
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }

    /** Whitelist: letters, digits, and underscore only. */
    public static boolean isValidUsername(String username) {
        return isWithinLength(username, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH)
                && USERNAME_PATTERN.matcher(username).matches();
    }

    /** Requires upper, lower, digit, and symbol, within the mandated length bounds. */
    public static boolean isValidPassword(String password) {
        if (!isWithinLength(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH)) {
            return false;
        }
        return PASSWORD_UPPER.matcher(password).find()
                && PASSWORD_LOWER.matcher(password).find()
                && PASSWORD_DIGIT.matcher(password).find()
                && PASSWORD_SYMBOL.matcher(password).find();
    }

    public static boolean isValidRoleCode(int role) {
        return role >= ROLE_MIN && role <= ROLE_MAX;
    }

    /** Parses strictly: no leading/trailing junk, no implicit truncation. */
    public static boolean isValidRoleCode(String roleText) {
        if (roleText == null || !roleText.matches("^-?\\d+$")) {
            return false;
        }
        try {
            return isValidRoleCode(Integer.parseInt(roleText));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static boolean isValidProductName(String name) {
        return isWithinLength(name, 1, PRODUCT_NAME_MAX_LENGTH)
                && Pattern.matches("^[A-Za-z0-9 _.\\-]+$", name);
    }

    public static boolean isNonNegativeInteger(String value) {
        if (value == null || !value.matches("^\\d+$")) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static boolean isNonNegativeDecimal(String value) {
        if (value == null || !value.matches("^\\d+(\\.\\d+)?$")) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
