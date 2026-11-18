package Controller;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * One-way salted password hashing using PBKDF2-HMAC-SHA256 (600,000 iterations, 128-bit random salt,
 * 256-bit output). Stored form: pbkdf2-sha256$iterations$saltBase64$hashBase64, so the work factor can
 * be raised later without breaking existing rows.
 */
public final class PasswordHasher {
    static final String PREFIX = "pbkdf2-sha256";
    static final int ITERATIONS = 600000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MAX_ACCEPTED_ITERATIONS = 5000000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String dummy;

    private PasswordHasher() { }

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** True only when the password matches; malformed stored values never match. Constant-time comparison. */
    public static boolean verify(char[] password, String stored) {
        try {
            String[] parts = stored == null ? null : stored.split("\\$", -1);
            if (parts == null || parts.length != 4 || !PREFIX.equals(parts[0])) { return false; }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > MAX_ACCEPTED_ITERATIONS) { return false; }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length < SALT_BYTES || expected.length == 0) { return false; }
            byte[] actual = derive(password, salt, iterations);
            boolean match = MessageDigest.isEqual(expected, actual);
            Arrays.fill(actual, (byte) 0);
            return match;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Spends the same effort as a real check so unknown usernames are not distinguishable by timing. */
    public static void verifyAgainstDummy(char[] password) {
        String value = dummy;
        if (value == null) { value = hash("Dummy#Password1".toCharArray()); dummy = value; }
        verify(password, value);
    }

    public static boolean isHashFormat(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$") && stored.split("\\$", -1).length == 4;
    }

    public static boolean needsRehash(String stored) {
        try { return Integer.parseInt(stored.split("\\$", -1)[1]) < ITERATIONS; }
        catch (RuntimeException ex) { return true; }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password == null ? new char[0] : password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Hashing unavailable");
        } finally {
            spec.clearPassword();
        }
    }
}
