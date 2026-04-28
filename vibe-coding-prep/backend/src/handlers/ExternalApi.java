package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import util.AppLogger;
import util.Http;

/**
 * Calls two free, no-key external APIs and returns their result to our
 * frontend. Going through the backend means:
 *   - The browser never has CORS problems.
 *   - We can log every external call.
 *   - We can swap APIs later without touching the UI.
 *
 *   GET /api/external/geocode?q=University+of+Mindanao
 *       -> { "lat": ..., "lng": ..., "display": "..." }
 *
 *   GET /api/external/avatar?name=Juan+Dela+Cruz
 *       -> { "url": "https://ui-avatars.com/..." }
 */
public class ExternalApi implements HttpHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/external/geocode") && ex.getRequestMethod().equals("GET")) { geocode(ex); return; }
            if (path.equals("/api/external/avatar")  && ex.getRequestMethod().equals("GET")) { avatar(ex);  return; }
            Http.error(ex, 404, "Unknown external endpoint.");
        } catch (Exception e) {
            AppLogger.error("ExternalApi failure on " + path, e);
            Http.error(ex, 502, "External API failed: " + e.getMessage());
        }
    }

    // ---------- GET /api/external/geocode --------------------------------
    private void geocode(HttpExchange ex) throws Exception {
        String q = Http.query(ex).getOrDefault("q", "").trim();
        if (q.isEmpty()) { Http.error(ex, 400, "Query parameter 'q' is required."); return; }

        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                   + URLEncoder.encode(q, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                // Nominatim asks for a User-Agent so they can contact the operator.
                .header("User-Agent", "CCE-Vibe-Coding-Lost-Found/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        AppLogger.activity("GEOCODE", null, "q=\"" + q + "\" status=" + res.statusCode());

        if (res.statusCode() != 200) {
            Http.error(ex, 502, "Nominatim returned " + res.statusCode()); return;
        }

        // The response is a JSON array: [{"lat":"...", "lon":"...", "display_name":"..."}]
        String body = res.body();
        Double lat = pickField(body, "\"lat\"");
        Double lng = pickField(body, "\"lon\"");
        String dis = pickString(body, "\"display_name\"");

        if (lat == null || lng == null) {
            Http.json(ex, 200, Map.of("found", false));
        } else {
            Http.json(ex, 200, Map.of("found", true, "lat", lat, "lng", lng, "display", dis));
        }
    }

    // ---------- GET /api/external/avatar ---------------------------------
    private void avatar(HttpExchange ex) throws Exception {
        String name = Http.query(ex).getOrDefault("name", "").trim();
        if (name.isEmpty()) { Http.error(ex, 400, "Query parameter 'name' is required."); return; }

        String url = "https://ui-avatars.com/api/?name="
                   + URLEncoder.encode(name, StandardCharsets.UTF_8)
                   + "&background=1B2B4A&color=fff&size=128";

        // We don't actually need to call the API – it's just a URL builder. But
        // we can quickly check it responds, to count it as a real integration.
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        AppLogger.activity("AVATAR", null, "name=\"" + name + "\" status=" + res.statusCode());

        Http.json(ex, 200, Map.of("url", url, "status", res.statusCode()));
    }

    // ---------- super tiny JSON pickers (no external library) -----------
    /** Find "key": "value" or "key": number — returns the number, or null. */
    private static Double pickField(String body, String quotedKey) {
        int i = body.indexOf(quotedKey);
        if (i < 0) return null;
        i = body.indexOf(':', i);
        if (i < 0) return null;
        i++;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '"')) i++;
        int start = i;
        while (i < body.length() && "0123456789.-".indexOf(body.charAt(i)) >= 0) i++;
        try { return Double.parseDouble(body.substring(start, i)); }
        catch (Exception e) { return null; }
    }
    private static String pickString(String body, String quotedKey) {
        int i = body.indexOf(quotedKey);
        if (i < 0) return null;
        i = body.indexOf(':', i);
        int start = body.indexOf('"', i + 1) + 1;
        int end   = body.indexOf('"', start);
        if (start <= 0 || end < 0) return null;
        return body.substring(start, end);
    }

    // For completeness if we ever want to forward a raw response body.
    @SuppressWarnings("unused")
    private static void writeRaw(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
