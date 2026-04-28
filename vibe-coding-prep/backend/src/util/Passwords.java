package util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Very small password helper.
 *
 * We use SHA-256 and store the result as 64 hex characters. This is fine for
 * a competition demo. For real production, switch to bcrypt or PBKDF2.
 */
public class Passwords {

    /** Convert a plain password into a 64-character hex string. */
    public static String hash(String plain) {
        if (plain == null) plain = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(plain.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /** True if the supplied plain password matches the stored hash. */
    public static boolean matches(String plain, String storedHash) {
        return hash(plain).equalsIgnoreCase(storedHash);
    }
}
