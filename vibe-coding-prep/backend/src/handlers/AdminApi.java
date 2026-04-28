package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import util.AppLogger;
import util.Database;
import util.Http;

/**
 * Endpoints only an ADMIN can hit.
 *
 *   GET /api/admin/users      list of users
 *   GET /api/admin/logs       latest 200 activity_logs entries
 *   GET /api/admin/stats      counts for the dashboard
 */
public class AdminApi implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (Http.requireRole(ex, "ADMIN") == null) return;

            if (path.equals("/api/admin/users") && ex.getRequestMethod().equals("GET")) { users(ex); return; }
            if (path.equals("/api/admin/logs")  && ex.getRequestMethod().equals("GET")) { logs(ex);  return; }
            if (path.equals("/api/admin/stats") && ex.getRequestMethod().equals("GET")) { stats(ex); return; }
            Http.error(ex, 404, "Unknown admin endpoint.");
        } catch (Exception e) {
            AppLogger.error("AdminApi failure on " + path, e);
            Http.error(ex, 500, "Internal error: " + e.getMessage());
        }
    }

    private void users(HttpExchange ex) throws Exception {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, full_name, username, role, avatar_url, created_at"
              + " FROM users ORDER BY created_at DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> u = new LinkedHashMap<>();
                    u.put("id",         rs.getInt("id"));
                    u.put("full_name",  rs.getString("full_name"));
                    u.put("username",   rs.getString("username"));
                    u.put("role",       rs.getString("role"));
                    u.put("avatar_url", rs.getString("avatar_url"));
                    u.put("created_at", String.valueOf(rs.getTimestamp("created_at")));
                    out.add(u);
                }
                Http.json(ex, 200, Map.of("users", out));
            }
        }
    }

    private void logs(HttpExchange ex) throws Exception {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, action, username, details, ip_address, created_at"
              + " FROM activity_logs ORDER BY created_at DESC LIMIT 200")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> l = new LinkedHashMap<>();
                    l.put("id",         rs.getInt("id"));
                    l.put("action",     rs.getString("action"));
                    l.put("username",   rs.getString("username"));
                    l.put("details",    rs.getString("details"));
                    l.put("ip_address", rs.getString("ip_address"));
                    l.put("created_at", String.valueOf(rs.getTimestamp("created_at")));
                    out.add(l);
                }
                Http.json(ex, 200, Map.of("logs", out));
            }
        }
    }

    private void stats(HttpExchange ex) throws Exception {
        try (Connection c = Database.get()) {
            Map<String,Object> stats = new LinkedHashMap<>();
            stats.put("users",   countOf(c, "SELECT COUNT(*) FROM users"));
            stats.put("items",   countOf(c, "SELECT COUNT(*) FROM items"));
            stats.put("lost",    countOf(c, "SELECT COUNT(*) FROM items WHERE type='LOST'"));
            stats.put("found",   countOf(c, "SELECT COUNT(*) FROM items WHERE type='FOUND'"));
            stats.put("claims",  countOf(c, "SELECT COUNT(*) FROM claims"));
            stats.put("logs",    countOf(c, "SELECT COUNT(*) FROM activity_logs"));
            Http.json(ex, 200, stats);
        }
    }

    private static int countOf(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getInt(1);
        }
    }
}
