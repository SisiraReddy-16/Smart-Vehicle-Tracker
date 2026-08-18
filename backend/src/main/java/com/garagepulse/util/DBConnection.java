package com.garagepulse.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Properties;

/**
 * Hands out pooled JDBC connections instead of opening a brand-new physical
 * connection on every request. Opening a raw TCP + MySQL handshake per
 * request is by far the most expensive (and most failure-prone) part of
 * every servlet call -- pooling a handful of already-authenticated
 * connections keeps memory/CPU use low and makes "the DB isn't reachable"
 * fail fast and clearly instead of hanging.
 *
 * The pool is intentionally simple (no external dependency): a bounded
 * stack of idle connections behind a lock. Good enough for a project this
 * size; swap for HikariCP/DBCP if this ever needs to scale further.
 */
public class DBConnection {

    private static final int MAX_POOL_SIZE = 10;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    private static String URL;
    private static String USER;
    private static String PASSWORD;

    private static final ArrayDeque<Connection> POOL = new ArrayDeque<>();
    private static final Object LOCK = new Object();
    private static int totalCreated = 0;

    static {
        try (InputStream in = DBConnection.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("db.properties not found on classpath");
            }
            Properties props = new Properties();
            props.load(in);

            URL = envOrDefault("DB_URL", props.getProperty("db.url"));
            USER = envOrDefault("DB_USER", props.getProperty("db.user"));
            PASSWORD = envOrDefault("DB_PASSWORD", props.getProperty("db.password"));

            Class.forName("com.mysql.cj.jdbc.Driver");
            DriverManager.setLoginTimeout(CONNECT_TIMEOUT_SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load DB configuration: " + e.getMessage(), e);
        }
    }

    private static String envOrDefault(String envKey, String fallback) {
        String value = System.getenv(envKey);
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    private DBConnection() { }

    /**
     * Borrows a pooled connection (creating a new physical one only when the
     * pool is empty and under its size cap). Every caller MUST use
     * try-with-resources on the returned Connection -- close() on a pooled
     * connection returns it to the pool instead of really closing it.
     */
    public static Connection get() throws SQLException {
        synchronized (LOCK) {
            while (!POOL.isEmpty()) {
                Connection candidate = POOL.pop();
                if (isUsable(candidate)) {
                    return wrap(candidate);
                }
                closeQuietly(candidate);
                totalCreated--;
            }
        }

        try {
            Connection fresh = DriverManager.getConnection(URL, USER, PASSWORD);
            synchronized (LOCK) { totalCreated++; }
            return wrap(fresh);
        } catch (SQLException e) {
            // Re-throw with a message the servlets/frontend can show as-is,
            // instead of a raw driver stack trace.
            throw new SQLException("Could not reach the database. Is MySQL running and reachable at "
                    + safeHost() + "? (" + e.getMessage() + ")", e.getSQLState(), e);
        }
    }

    private static boolean isUsable(Connection c) {
        try {
            return c != null && !c.isClosed() && c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection c) {
        try { if (c != null) c.close(); } catch (SQLException ignored) { }
    }

    private static String safeHost() {
        // Strip credentials-free host:port out of the JDBC URL for error messages.
        try {
            String withoutProto = URL.substring(URL.indexOf("//") + 2);
            int slash = withoutProto.indexOf('/');
            return slash > -1 ? withoutProto.substring(0, slash) : withoutProto;
        } catch (Exception e) {
            return "the configured host";
        }
    }

    /** Wraps a real connection so close() returns it to the pool instead of destroying it. */
    private static Connection wrap(Connection real) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> {
                if ("close".equals(method.getName())) {
                    release(real);
                    return null;
                }
                try {
                    return method.invoke(real, args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getCause();
                }
            }
        );
    }

    private static void release(Connection real) {
        try {
            // Never hand back a connection with leftover transaction state
            // or a dirty auto-commit flag to the next borrower.
            if (!real.getAutoCommit()) {
                try { real.rollback(); } catch (SQLException ignored) { }
                real.setAutoCommit(true);
            }
        } catch (SQLException e) {
            closeQuietly(real);
            synchronized (LOCK) { totalCreated--; }
            return;
        }

        synchronized (LOCK) {
            if (POOL.size() < MAX_POOL_SIZE && isUsable(real)) {
                POOL.push(real);
            } else {
                closeQuietly(real);
                totalCreated--;
            }
        }
    }
}
