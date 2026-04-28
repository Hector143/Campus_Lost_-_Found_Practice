package util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logger.
 *
 * Writes to two places:
 *   1. backend/app.log     - plain text, one line per event
 *   2. activity_logs table - searchable from the admin page
 *
 * If the database write fails we still keep the file write so you can debug.
 *
 * Usage:
 *   AppLogger.info("Server started");
 *   AppLogger.warn("Bad password for user " + u);
 *   AppLogger.error("DB down", ex);
 *   AppLogger.activity("LOGIN", username, "ip=127.0.0.1");
 */
public class AppLogger {

    private static final String FILE = "app.log";
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppLogger() {}

    public static void info (String msg)             { write("INFO",  msg, null); }
    public static void warn (String msg)             { write("WARN",  msg, null); }
    public static void error(String msg)             { write("ERROR", msg, null); }
    public static void error(String msg, Throwable t){ write("ERROR", msg, t); }

    /** Records an interesting business action AND prints it. */
    public static void activity(String action, String username, String details) {
        write("ACT", action + " by " + (username == null ? "anon" : username)
                + " - " + (details == null ? "" : details), null);
        try (java.sql.Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO activity_logs(action, username, details) VALUES (?,?,?)")) {
            ps.setString(1, action);
            ps.setString(2, username);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (Throwable t) {
            // We don't want logging to crash the request.
            write("ERROR", "Failed to write activity row: " + t.getMessage(), null);
        }
    }

    // ---- internals ------------------------------------------------------

    private static void write(String level, String msg, Throwable t) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + msg;
        System.out.println(line);
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE, true))) {
            pw.println(line);
            if (t != null) t.printStackTrace(pw);
        } catch (Exception ignore) {
            // Last resort: even file write failed. Nothing more we can do.
        }
    }

}
