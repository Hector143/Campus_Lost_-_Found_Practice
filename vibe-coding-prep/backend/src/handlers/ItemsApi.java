package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import util.AppLogger;
import util.Database;
import util.Http;
import util.Json;
import util.Sessions;

/**
 * Handles /api/items and /api/items/{id} (and /api/items/{id}/claims).
 *
 *   GET    /api/items                  list   (everyone)
 *          ?type=LOST&q=wallet         optional filters
 *   POST   /api/items                  create (logged-in)
 *   GET    /api/items/{id}             one    (everyone)
 *   DELETE /api/items/{id}             owner or admin
 *   GET    /api/items/{id}/claims      claims for one item (owner or admin)
 */
public class ItemsApi implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            // /api/items
            if (path.equals("/api/items")) {
                if (method.equals("GET"))  { list(ex);   return; }
                if (method.equals("POST")) { create(ex); return; }
                Http.error(ex, 405, "Method not allowed."); return;
            }
            // /api/items/{id} or /api/items/{id}/claims
            if (path.startsWith("/api/items/")) {
                String rest = path.substring("/api/items/".length());
                if (rest.endsWith("/claims")) {
                    int id = parseInt(rest.substring(0, rest.length() - "/claims".length()));
                    if (method.equals("GET"))  { listClaims(ex, id); return; }
                    Http.error(ex, 405, "Method not allowed."); return;
                }
                int id = parseInt(rest);
                if (method.equals("GET"))    { one(ex, id);    return; }
                if (method.equals("DELETE")) { delete(ex, id); return; }
                Http.error(ex, 405, "Method not allowed."); return;
            }
            Http.error(ex, 404, "Unknown items endpoint.");
        } catch (NumberFormatException nfe) {
            Http.error(ex, 400, "Bad item id.");
        } catch (Exception e) {
            AppLogger.error("ItemsApi failure on " + path, e);
            Http.error(ex, 500, "Internal error: " + e.getMessage());
        }
    }

    // ---------- GET /api/items?type=&q= -------------------------------------
    private void list(HttpExchange ex) throws Exception {
        Map<String,String> q = Http.query(ex);
        String type   = q.getOrDefault("type", "").trim();
        String search = q.getOrDefault("q",    "").trim();

        StringBuilder sql = new StringBuilder(
            "SELECT i.*, u.full_name AS reporter_name, u.avatar_url AS reporter_avatar"
          + " FROM items i JOIN users u ON u.id = i.reporter_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!type.isEmpty()) { sql.append(" AND i.type = ?");                args.add(type); }
        if (!search.isEmpty()) {
            sql.append(" AND (i.title LIKE ? OR i.description LIKE ? OR i.location LIKE ?)");
            String like = "%" + search + "%";
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY i.created_at DESC LIMIT 200");

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) out.add(rowToMap(rs));
                Http.json(ex, 200, Map.of("items", out, "count", out.size()));
            }
        }
    }

    // ---------- POST /api/items ------------------------------------------
    private void create(HttpExchange ex) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;

        Map<String,Object> body = Json.parseObject(Http.readBody(ex));
        String type        = str(body, "type");
        String title       = str(body, "title");
        String description = str(body, "description");
        String category    = str(body, "category");
        String location    = str(body, "location");
        Double lat = num(body, "latitude");
        Double lng = num(body, "longitude");

        if (!type.equals("LOST") && !type.equals("FOUND")) {
            Http.error(ex, 400, "type must be LOST or FOUND."); return;
        }
        if (title.isEmpty()) { Http.error(ex, 400, "Title is required."); return; }

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO items(type, title, description, category, location,"
              + " latitude, longitude, reporter_id) VALUES (?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, category);
            ps.setString(5, location);
            if (lat != null) ps.setBigDecimal(6, BigDecimal.valueOf(lat)); else ps.setNull(6, java.sql.Types.DECIMAL);
            if (lng != null) ps.setBigDecimal(7, BigDecimal.valueOf(lng)); else ps.setNull(7, java.sql.Types.DECIMAL);
            ps.setInt(8, s.userId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                int newId = rs.getInt(1);
                AppLogger.activity("CREATE_ITEM", s.username, "id=" + newId + " type=" + type);
                Http.json(ex, 201, Map.of("ok", true, "id", newId));
            }
        }
    }

    // ---------- GET /api/items/{id} --------------------------------------
    private void one(HttpExchange ex, int id) throws Exception {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT i.*, u.full_name AS reporter_name, u.avatar_url AS reporter_avatar"
              + " FROM items i JOIN users u ON u.id = i.reporter_id WHERE i.id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { Http.error(ex, 404, "Item not found."); return; }
                Http.json(ex, 200, rowToMap(rs));
            }
        }
    }

    // ---------- DELETE /api/items/{id} -----------------------------------
    private void delete(HttpExchange ex, int id) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;

        try (Connection c = Database.get()) {
            int ownerId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT reporter_id FROM items WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { Http.error(ex, 404, "Item not found."); return; }
                    ownerId = rs.getInt(1);
                }
            }
            if (ownerId != s.userId && !s.role.equals("ADMIN")) {
                Http.error(ex, 403, "Only the owner or an admin can delete this item."); return;
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM items WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            AppLogger.activity("DELETE_ITEM", s.username, "id=" + id);
            Http.json(ex, 200, Map.of("ok", true));
        }
    }

    // ---------- GET /api/items/{id}/claims -------------------------------
    private void listClaims(HttpExchange ex, int itemId) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;

        try (Connection c = Database.get()) {
            int ownerId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT reporter_id FROM items WHERE id=?")) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { Http.error(ex, 404, "Item not found."); return; }
                    ownerId = rs.getInt(1);
                }
            }
            if (ownerId != s.userId && !s.role.equals("ADMIN")) {
                Http.error(ex, 403, "Only the owner or an admin can see the claims."); return;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cl.*, u.full_name AS claimer_name, u.username AS claimer_username,"
                  + "        u.avatar_url AS claimer_avatar"
                  + " FROM claims cl JOIN users u ON u.id = cl.claimer_id"
                  + " WHERE cl.item_id=? ORDER BY cl.created_at DESC")) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String,Object>> out = new ArrayList<>();
                    while (rs.next()) {
                        Map<String,Object> r = new LinkedHashMap<>();
                        r.put("id",               rs.getInt("id"));
                        r.put("item_id",          rs.getInt("item_id"));
                        r.put("claimer_name",     rs.getString("claimer_name"));
                        r.put("claimer_username", rs.getString("claimer_username"));
                        r.put("claimer_avatar",   rs.getString("claimer_avatar"));
                        r.put("message",          rs.getString("message"));
                        r.put("status",           rs.getString("status"));
                        r.put("created_at",       String.valueOf(rs.getTimestamp("created_at")));
                        out.add(r);
                    }
                    Http.json(ex, 200, Map.of("claims", out));
                }
            }
        }
    }

    // ---------- helpers --------------------------------------------------
    private static Map<String,Object> rowToMap(ResultSet rs) throws Exception {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("id",               rs.getInt("id"));
        r.put("type",             rs.getString("type"));
        r.put("title",            rs.getString("title"));
        r.put("description",      rs.getString("description"));
        r.put("category",         rs.getString("category"));
        r.put("location",         rs.getString("location"));
        BigDecimal la = rs.getBigDecimal("latitude");
        BigDecimal lo = rs.getBigDecimal("longitude");
        r.put("latitude",         la == null ? null : la.doubleValue());
        r.put("longitude",        lo == null ? null : lo.doubleValue());
        r.put("status",           rs.getString("status"));
        r.put("reporter_id",      rs.getInt("reporter_id"));
        r.put("reporter_name",    rs.getString("reporter_name"));
        r.put("reporter_avatar",  rs.getString("reporter_avatar"));
        r.put("created_at",       String.valueOf(rs.getTimestamp("created_at")));
        return r;
    }
    private static int parseInt(String s) { return Integer.parseInt(s); }
    private static String str(Map<String,Object> m, String k) {
        Object v = m.get(k); return v == null ? "" : v.toString().trim();
    }
    private static Double num(Map<String,Object> m, String k) {
        Object v = m.get(k); if (v == null) return null;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }
}
