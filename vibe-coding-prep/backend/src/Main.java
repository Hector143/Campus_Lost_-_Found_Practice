import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import handlers.AdminApi;
import handlers.AuthApi;
import handlers.ClaimsApi;
import handlers.ExternalApi;
import handlers.ItemsApi;
import handlers.StaticFiles;
import util.AppLogger;
import util.Database;

/**
 * Application entry point.
 *
 * Starts the built-in JDK HTTP server (no external framework) and registers
 * all the routes. The server keeps running until you press Ctrl+C.
 *
 * Routes:
 *   /                 -> static files (frontend/index.html)
 *   /api/auth/*       -> AuthApi      (signup, login, logout, me)
 *   /api/items*       -> ItemsApi     (list, create, get, delete)
 *   /api/claims*      -> ClaimsApi    (claim, approve, reject)
 *   /api/admin/*      -> AdminApi     (users, logs)         [ADMIN only]
 *   /api/external/*   -> ExternalApi  (geocode, avatar)
 */
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        // 1. Open the database connection up-front so we fail fast if it is wrong.
        Database.init();

        // 2. Create the HTTP server.
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 3. Register routes. Order matters: longer paths first.
        server.createContext("/api/auth/",     new AuthApi());
        server.createContext("/api/items",     new ItemsApi());
        server.createContext("/api/claims",    new ClaimsApi());
        server.createContext("/api/admin/",    new AdminApi());
        server.createContext("/api/external/", new ExternalApi());
        server.createContext("/",              new StaticFiles("frontend"));

        // 4. Use a small thread pool so multiple browsers can hit it at once.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));

        server.start();
        AppLogger.info("Server started at http://localhost:" + PORT);
        System.out.println("[INFO] Open http://localhost:" + PORT + " in your browser.");
        System.out.println("[INFO] Press Ctrl+C to stop.");
    }
}
