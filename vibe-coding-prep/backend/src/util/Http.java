package util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helpers used by every handler: read the body, write JSON, get the
 * client IP, parse the query string, and require a logged-in user.
 */
public class Http {

    /** Read the full request body as a UTF-8 string. */
    public static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Send a JSON response with the given status code. */
    public static void json(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Quick error response: { "error": "<msg>" }. */
    public static void error(HttpExchange ex, int status, String msg) throws IOException {
        json(ex, status, Map.of("error", msg));
    }

    /** Returns the bearer token from the Authorization header, or null. */
    public static String token(HttpExchange ex) {
        return Sessions.tokenFromHeader(ex.getRequestHeaders().getFirst("Authorization"));
    }

    /**
     * Returns the logged-in session, or sends 401 and returns null.
     * Caller pattern:
     *   var s = Http.requireUser(ex); if (s == null) return;
     */
    public static Sessions.SessionInfo requireUser(HttpExchange ex) throws IOException {
        Sessions.SessionInfo s = Sessions.find(token(ex));
        if (s == null) { error(ex, 401, "Not logged in."); return null; }
        return s;
    }

    /** Same as requireUser but also checks the role. */
    public static Sessions.SessionInfo requireRole(HttpExchange ex, String role) throws IOException {
        Sessions.SessionInfo s = requireUser(ex);
        if (s == null) return null;
        if (!role.equalsIgnoreCase(s.role)) { error(ex, 403, "Forbidden."); return null; }
        return s;
    }

    /** Get the requester's IP address as best we can. */
    public static String ip(HttpExchange ex) {
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isEmpty()) return fwd.split(",")[0].trim();
        return ex.getRemoteAddress() == null ? "" : ex.getRemoteAddress().getAddress().getHostAddress();
    }

    /** Parse "?a=1&b=hello%20world" into a Map. */
    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) return out;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? ""   : part.substring(eq + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }
}
