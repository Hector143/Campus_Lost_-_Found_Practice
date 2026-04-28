package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import util.AppLogger;
import util.Database;
import util.Http;
import util.Json;
import util.Passwords;
import util.Sessions;

/**
 * Handles every URL that starts with /api/auth/.
 *
 *   POST /api/auth/signup    body: {full_name, username, password}
 *   POST /api/auth/login     body: {username, password}    -> {token, user}
 *   POST /api/auth/logout    header: Authorization: Bearer <token>
 *   GET  /api/auth/me        header: Authorization: Bearer <token>
 */
public class AuthApi implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if (path.equals("/api/auth/signup") && method.equals("POST")) { signup(ex); return; }
            if (path.equals("/api/auth/login")  && method.equals("POST")) { login(ex);  return; }
            if (path.equals("/api/auth/logout") && method.equals("POST")) { logout(ex); return; }
            if (path.equals("/api/auth/me")     && method.equals("GET"))  { me(ex);     return; }
            Http.error(ex, 404, "Unknown auth endpoint.");
        } catch (Exception e) {
            AppLogger.error("AuthApi failure on " + path, e);
            Http.error(ex, 500, "Internal error: " + e.getMessage());
        }
    }

    // ---------- POST /api/auth/signup ------------------------------------
    private void signup(HttpExchange ex) throws Exception {
        Map<String,Object> body = Json.parseObject(Http.readBody(ex));
        String fullName = str(body, "full_name");
        String username = str(body, "username");
        String password = str(body, "password");

        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Http.error(ex, 400, "full_name, username and password are required."); return;
        }
        if (password.length() < 6) {
            Http.error(ex, 400, "Password must be at least 6 characters."); return;
        }

        try (Connection c = Database.get()) {
            // Username must be unique
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT id FROM users WHERE username=?")) {
                check.setString(1, username);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) { Http.error(ex, 409, "Username already taken."); return; }
                }
            }
            String avatar = "https://ui-avatars.com/api/?name="
                    + java.net.URLEncoder.encode(fullName, "UTF-8")
                    + "&background=1B2B4A&color=fff";
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO users(full_name, username, password_hash, role, avatar_url)"
                  + " VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, fullName);
                ins.setString(2, username);
                ins.setString(3, Passwords.hash(password));
                ins.setString(4, "STUDENT");
                ins.setString(5, avatar);
                ins.executeUpdate();
            }
        }

        AppLogger.activity("SIGNUP", username, "ip=" + Http.ip(ex));
        Http.json(ex, 201, Map.of("ok", true));
    }

    // ---------- POST /api/auth/login -------------------------------------
    private void login(HttpExchange ex) throws Exception {
        Map<String,Object> body = Json.parseObject(Http.readBody(ex));
        String username = str(body, "username");
        String password = str(body, "password");

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, full_name, password_hash, role, avatar_url FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !Passwords.matches(password, rs.getString("password_hash"))) {
                    AppLogger.activity("LOGIN_FAILED", username, "ip=" + Http.ip(ex));
                    Http.error(ex, 401, "Invalid username or password."); return;
                }
                int    id    = rs.getInt("id");
                String role  = rs.getString("role");
                String name  = rs.getString("full_name");
                String avtr  = rs.getString("avatar_url");

                String token = Sessions.create(id, username, role);
                AppLogger.activity("LOGIN", username, "ip=" + Http.ip(ex));

                Map<String,Object> user = new LinkedHashMap<>();
                user.put("id", id);
                user.put("username", username);
                user.put("full_name", name);
                user.put("role", role);
                user.put("avatar_url", avtr);
                Http.json(ex, 200, Map.of("token", token, "user", user));
            }
        }
    }

    // ---------- POST /api/auth/logout ------------------------------------
    private void logout(HttpExchange ex) throws IOException {
        String t = Http.token(ex);
        Sessions.SessionInfo s = Sessions.find(t);
        Sessions.destroy(t);
        if (s != null) AppLogger.activity("LOGOUT", s.username, "ip=" + Http.ip(ex));
        Http.json(ex, 200, Map.of("ok", true));
    }

    // ---------- GET /api/auth/me -----------------------------------------
    private void me(HttpExchange ex) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, username, full_name, role, avatar_url FROM users WHERE id=?")) {
            ps.setInt(1, s.userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { Http.error(ex, 404, "User missing."); return; }
                Map<String,Object> u = new LinkedHashMap<>();
                u.put("id", rs.getInt("id"));
                u.put("username", rs.getString("username"));
                u.put("full_name", rs.getString("full_name"));
                u.put("role", rs.getString("role"));
                u.put("avatar_url", rs.getString("avatar_url"));
                Http.json(ex, 200, u);
            }
        }
    }

    private static String str(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString().trim();
    }
}
