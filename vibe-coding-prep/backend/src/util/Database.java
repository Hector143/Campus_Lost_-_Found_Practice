package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Thin wrapper around JDBC.
 *
 * Every handler asks for a fresh Connection via {@link #get()} and closes it
 * when done (try-with-resources). For a 3-hour competition this is plenty —
 * we don't need a real connection pool.
 *
 * Connection settings come from environment variables first, then fall back
 * to the constants below. Edit the constants OR set DB_URL / DB_USER / DB_PASS.
 */
public class Database {

    // ---- EDIT THESE if you don't want to use environment variables ----
    private static final String DEFAULT_URL  = "jdbc:mysql://localhost:3306/lost_and_found";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "563565";
    // -------------------------------------------------------------------

    private static String url;
    private static String user;
    private static String pass;

    /** Called once at startup. Loads the driver and tests the connection. */
    public static void init() throws SQLException {
        url  = envOr("DB_URL",  DEFAULT_URL);
        user = envOr("DB_USER", DEFAULT_USER);
        pass = envOr("DB_PASS", DEFAULT_PASS);

        try {
            // Force the JDBC driver to register itself.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "MySQL JDBC driver not found. Put mysql-connector-j-*.jar inside backend/lib/", e);
        }

        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            AppLogger.info("Database connected: " + c.getCatalog());
        }
    }

    /** Open a NEW connection. The caller MUST close it. */
    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
