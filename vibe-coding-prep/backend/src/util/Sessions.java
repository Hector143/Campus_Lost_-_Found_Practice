package util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store.
 *
 * After a successful login we hand the client a random 32-byte token. The
 * client sends it back as <code>Authorization: Bearer &lt;token&gt;</code>.
 * The token maps to a {@link SessionInfo} kept in a map.
 *
 * Tokens disappear when the server restarts — that is fine for a 3-hour
 * competition. For production you would store sessions in Redis or DB.
 */
public class Sessions {

    /** What we know about a logged-in user. */
    public static class SessionInfo {
        public final int    userId;
        public final String username;
        public final String role;     // "STUDENT" or "ADMIN"

        public SessionInfo(int userId, String username, String role) {
            this.userId   = userId;
            this.username = username;
            this.role     = role;
        }
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static final Map<String, SessionInfo> STORE = new ConcurrentHashMap<>();

    /** Create a brand-new session and return its token. */
    public static String create(int userId, String username, String role) {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        STORE.put(token, new SessionInfo(userId, username, role));
        return token;
    }

    /** Look up the session that owns the given token, or null. */
    public static SessionInfo find(String token) {
        if (token == null) return null;
        return STORE.get(token);
    }

    /** Forget a session (logout). */
    public static void destroy(String token) {
        if (token != null) STORE.remove(token);
    }

    /** Read the bearer token from an Authorization header. */
    public static String tokenFromHeader(String header) {
        if (header == null) return null;
        if (header.startsWith("Bearer ")) return header.substring(7).trim();
        return header.trim();
    }
}
