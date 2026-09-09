package com.garagepulse.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Properties;

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

            URL = envOrDefault("DB_URL", props.getProperty("db.url"), "db.url");
            USER = envOrDefault("DB_USER", props.getProperty("db.user"), "db.user");
            PASSWORD = envOrDefault("DB_PASSWORD", props.getProperty("db.password"), "db.password");

            Class.forName("com.mysql.cj.jdbc.Driver");
            DriverManager.setLoginTimeout(CONNECT_TIMEOUT_SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load DB configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a config value from an env var first, db.properties second -
     * and PRINTS which one won, plus a masked preview, so it's never a
     * silent mystery which credentials are actually in use. Check your
     * Tomcat/console log on startup if login/signup ever behaves
     * unexpectedly - this line tells you immediately.
     */
    private static String envOrDefault(String envKey, String fallback, String propKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) {
            System.out.println("[GaragePulse] " + propKey + " <- environment variable " + envKey
                    + " = " + mask(value) + "  (overrides db.properties)");
            return value;
        }
        System.out.println("[GaragePulse] " + propKey + " <- db.properties = " + mask(fallback));
        return fallback;
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) return "(empty)";
        if (value.length() <= 2) return "**";
        return value.charAt(0) + "***" + value.charAt(value.length() - 1) + " (" + value.length() + " chars)";
    }

    private DBConnection() { }

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
        try {
            String withoutProto = URL.substring(URL.indexOf("//") + 2);
            int slash = withoutProto.indexOf('/');
            return slash > -1 ? withoutProto.substring(0, slash) : withoutProto;
        } catch (Exception e) {
            return "the configured host";
        }
    }

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