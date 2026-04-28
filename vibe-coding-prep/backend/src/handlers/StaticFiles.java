package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import util.AppLogger;

/**
 * Serves files from a folder on disk (the frontend/ directory).
 *
 * Rules:
 *   - "/"            -> index.html
 *   - "/foo.html"    -> frontend/foo.html
 *   - "/style.css"   -> frontend/style.css (text/css)
 *   - directory traversal ("..") is blocked
 */
public class StaticFiles implements HttpHandler {

    private final File root;

    public StaticFiles(String folder) {
        this.root = new File(folder).getAbsoluteFile();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String urlPath = ex.getRequestURI().getPath();
        if (urlPath.equals("/")) urlPath = "/index.html";

        // Block ".." attempts so we cannot leak files outside the frontend folder.
        if (urlPath.contains("..")) {
            send(ex, 400, "text/plain", "Bad path".getBytes()); return;
        }

        File f = new File(root, urlPath);
        if (!f.getCanonicalPath().startsWith(root.getCanonicalPath())) {
            send(ex, 400, "text/plain", "Bad path".getBytes()); return;
        }
        if (!f.exists() || f.isDirectory()) {
            send(ex, 404, "text/plain", ("Not found: " + urlPath).getBytes());
            return;
        }

        String mime = guessMime(urlPath);
        try {
            byte[] body = Files.readAllBytes(f.toPath());
            send(ex, 200, mime, body);
        } catch (IOException e) {
            AppLogger.error("StaticFiles read failed: " + urlPath, e);
            send(ex, 500, "text/plain", ("Error: " + e.getMessage()).getBytes());
        }
    }

    private static void send(HttpExchange ex, int status, String mime, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", mime);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static String guessMime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".css"))  return "text/css; charset=utf-8";
        if (p.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".png"))  return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg"))  return "image/svg+xml";
        if (p.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }
}
