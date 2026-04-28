package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import util.AppLogger;
import util.Database;
import util.Http;
import util.Json;
import util.Sessions;

/**
 * Handles /api/claims*.
 *
 *   POST /api/claims              body: {item_id, message}        - student claims an item
 *   POST /api/claims/{id}/approve  - item owner accepts a claim
 *   POST /api/claims/{id}/reject   - item owner rejects a claim
 */
public class ClaimsApi implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if (path.equals("/api/claims") && method.equals("POST")) { create(ex); return; }

            if (path.startsWith("/api/claims/") && method.equals("POST")) {
                String rest = path.substring("/api/claims/".length());
                if (rest.endsWith("/approve")) {
                    decide(ex, parseId(rest, "/approve"), true); return;
                }
                if (rest.endsWith("/reject")) {
                    decide(ex, parseId(rest, "/reject"), false); return;
                }
            }
            Http.error(ex, 404, "Unknown claims endpoint.");
        } catch (NumberFormatException nfe) {
            Http.error(ex, 400, "Bad claim id.");
        } catch (Exception e) {
            AppLogger.error("ClaimsApi failure on " + path, e);
            Http.error(ex, 500, "Internal error: " + e.getMessage());
        }
    }

    // ---------- POST /api/claims -----------------------------------------
    private void create(HttpExchange ex) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;

        Map<String,Object> body = Json.parseObject(Http.readBody(ex));
        int    itemId  = ((Number) body.getOrDefault("item_id", -1)).intValue();
        String message = str(body, "message");
        if (itemId <= 0) { Http.error(ex, 400, "item_id is required."); return; }

        try (Connection c = Database.get()) {
            int ownerId;
            String status;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT reporter_id, status FROM items WHERE id=?")) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { Http.error(ex, 404, "Item not found."); return; }
                    ownerId = rs.getInt("reporter_id");
                    status  = rs.getString("status");
                }
            }
            if (ownerId == s.userId)         { Http.error(ex, 400, "You cannot claim your own item."); return; }
            if (!"OPEN".equals(status))      { Http.error(ex, 400, "Item is no longer open.");        return; }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO claims(item_id, claimer_id, message) VALUES (?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, itemId);
                ps.setInt(2, s.userId);
                ps.setString(3, message);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next(); int id = rs.getInt(1);
                    AppLogger.activity("CREATE_CLAIM", s.username, "claim=" + id + " item=" + itemId);
                    Http.json(ex, 201, Map.of("ok", true, "id", id));
                }
            }
        }
    }

    // ---------- POST /api/claims/{id}/approve|reject ---------------------
    private void decide(HttpExchange ex, int claimId, boolean approve) throws Exception {
        Sessions.SessionInfo s = Http.requireUser(ex);
        if (s == null) return;

        try (Connection c = Database.get()) {
            int itemId, ownerId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cl.item_id, i.reporter_id FROM claims cl"
                  + " JOIN items i ON i.id = cl.item_id WHERE cl.id=?")) {
                ps.setInt(1, claimId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { Http.error(ex, 404, "Claim not found."); return; }
                    itemId  = rs.getInt(1);
                    ownerId = rs.getInt(2);
                }
            }
            if (ownerId != s.userId && !s.role.equals("ADMIN")) {
                Http.error(ex, 403, "Only the item owner can decide this claim."); return;
            }

            String newStatus = approve ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE claims SET status=? WHERE id=?")) {
                ps.setString(1, newStatus);
                ps.setInt(2, claimId);
                ps.executeUpdate();
            }
            if (approve) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE items SET status='CLAIMED' WHERE id=?")) {
                    ps.setInt(1, itemId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE claims SET status='REJECTED' WHERE item_id=? AND id<>? AND status='PENDING'")) {
                    ps.setInt(1, itemId);
                    ps.setInt(2, claimId);
                    ps.executeUpdate();
                }
            }
            AppLogger.activity(approve ? "APPROVE_CLAIM" : "REJECT_CLAIM",
                               s.username, "claim=" + claimId);
            Http.json(ex, 200, Map.of("ok", true, "status", newStatus));
        }
    }

    // ---------- helpers --------------------------------------------------
    private static int parseId(String rest, String suffix) {
        return Integer.parseInt(rest.substring(0, rest.length() - suffix.length()));
    }
    private static String str(Map<String,Object> m, String k) {
        Object v = m.get(k); return v == null ? "" : v.toString().trim();
    }
}
