package com.habitflow.config;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

// JDBC: connects to H2 locally or PostgreSQL in production.
public final class Database {
    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        String url = setting("habit.tracker.jdbc.url", "JDBC_URL");
        if (url == null) {
            url = localUrl();
        }
        if (isProduction() && !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("Production must use a PostgreSQL JDBC_URL.");
        }
        loadDriver(url);
        String user = setting("habit.tracker.jdbc.user", "JDBC_USER");
        String password = setting("habit.tracker.jdbc.password", "JDBC_PASSWORD");
        if (isProduction() && (user == null || password == null)) {
            throw new IllegalStateException("Set JDBC_USER and JDBC_PASSWORD for production.");
        }
        return DriverManager.getConnection(url, user == null ? "sa" : user, password == null ? "" : password);
    }

    public static boolean isPostgres() {
        String url = setting("habit.tracker.jdbc.url", "JDBC_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    public static void initialize() {
        try (Connection connection = getConnection();
             InputStream input = Database.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new IllegalStateException("schema.sql was not found on the classpath");
            }
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String command : schema.split(";")) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            }
            runMigrations(connection);
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Unable to initialize the database", exception);
        }
    }

    // Versioned additive migrations preserve existing habit records; legacy shared rows stay unassigned.
    private static void runMigrations(Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(80) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        String[] migrations = {"V001__user_accounts.sql", "V002__workspace_sync.sql", "V003__light_theme_default.sql"};
        for (String migration : migrations) {
            try (PreparedStatementCheck applied = new PreparedStatementCheck(connection, migration)) {
                if (applied.exists()) continue;
            }
            String resource = "/db/migration/" + migration;
            try (InputStream input = Database.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException(resource + " was not found on the classpath");
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String command : sql.split(";")) {
                        if (!command.isBlank()) statement.execute(command);
                    }
                    try (var insert = connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) {
                        insert.setString(1, migration);
                        insert.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    connection.rollback();
                    throw failure;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
        }
    }

    private static final class PreparedStatementCheck implements AutoCloseable {
        private final java.sql.PreparedStatement statement;
        private PreparedStatementCheck(Connection connection, String version) throws SQLException {
            statement = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?");
            statement.setString(1, version);
        }
        private boolean exists() throws SQLException {
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
        @Override public void close() throws SQLException { statement.close(); }
    }

    private static String localUrl() {
        if (isProduction()) {
            throw new IllegalStateException("Set JDBC_URL to a persistent PostgreSQL database for production.");
        }
        String dataDirectory = setting("habit.tracker.data.dir", "APP_DATA_DIR");
        String databaseFile = dataDirectory == null
                ? Path.of(System.getProperty("catalina.base", "."), "data", "habitflow")
                    .toAbsolutePath().toString().replace('\\', '/')
                : Path.of(dataDirectory, "habitflow").toString().replace('\\', '/');
        return "jdbc:h2:" + databaseFile;
    }

    private static void loadDriver(String url) {
        String driver = url.startsWith("jdbc:postgresql:") ? "org.postgresql.Driver" : "org.h2.Driver";
        try {
            Class.forName(driver, true, Database.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Database driver is missing: " + driver, exception);
        }
    }

    private static boolean isProduction() {
        String environment = setting("habit.tracker.environment", "APP_ENV");
        return "production".equalsIgnoreCase(environment);
    }

    private static String setting(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }
}
