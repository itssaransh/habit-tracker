package com.habitflow.dao;

import com.habitflow.config.Database;
import com.habitflow.service.AuthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Runs only with -Ppostgres-integration and a dedicated local *_test database.
class PostgresIT {
    private static final LocalDate TODAY = LocalDate.now();
    private static JdbcHabitDao habits;
    private static JdbcWorkspaceDao workspace;
    private static AuthService auth;

    @BeforeAll
    static void connectToDedicatedLocalDatabase() {
        String url = required("habitflow.pg.url");
        String user = required("habitflow.pg.user");
        String password = required("habitflow.pg.password");
        if (url.contains("@")) throw new IllegalStateException("Put credentials in separate environment variables, not the URL.");
        URI parsed = URI.create(url.substring("jdbc:postgresql:".length()));
        String host = parsed.getHost();
        String database = parsed.getPath() == null ? "" : parsed.getPath().replaceFirst("^/", "");
        if (!url.startsWith("jdbc:postgresql://") || host == null
                || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"))
                || !database.toLowerCase().contains("test")) {
            throw new IllegalStateException("PostgreSQL integration tests only allow a local database whose name includes 'test'.");
        }
        System.setProperty("habit.tracker.environment", "test");
        System.setProperty("habit.tracker.jdbc.url", url);
        System.setProperty("habit.tracker.jdbc.user", user);
        System.setProperty("habit.tracker.jdbc.password", password);
        Database.initialize();
        habits = new JdbcHabitDao();
        workspace = new JdbcWorkspaceDao();
        auth = new AuthService(new JdbcUserDao());
    }

    @Test
    void migrationsAndOwnershipConstraintsArePresent() throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT version FROM schema_migrations WHERE version IN (?, ?, ?)")) {
            statement.setString(1, "V001__user_accounts.sql");
            statement.setString(2, "V002__workspace_sync.sql");
            statement.setString(3, "V003__light_theme_default.sql");
            try (var rows = statement.executeQuery()) {
                int versions = 0;
                while (rows.next()) versions++;
                assertEquals(3, versions, "All versioned migrations must be recorded");
            }
        }

        var owner = newAccount("constraints");
        var other = newAccount("other");
        long habitId = habits.save(owner.id(), "Constraint habit", "", "Mind", "Daily", "#5B5FEF");
        assertSqlState("23503", () -> {
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                    "INSERT INTO habit_checkins(habit_id,checkin_date,user_id) VALUES (?,?,?)")) {
                p.setLong(1, habitId); p.setDate(2, Date.valueOf(TODAY)); p.setLong(3, other.id()); p.executeUpdate();
            }
        });
        habits.addCheckin(owner.id(), habitId, TODAY, "first");
        assertSqlState("23505", () -> {
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                    "INSERT INTO habit_checkins(habit_id,checkin_date,note,user_id) VALUES (?,?,?,?)")) {
                p.setLong(1, habitId); p.setDate(2, Date.valueOf(TODAY)); p.setString(3, "duplicate");
                p.setLong(4, owner.id()); p.executeUpdate();
            }
        });
        assertSqlState("23505", () -> {
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                    "INSERT INTO users(email,password_hash,display_name) VALUES (?,?,?)")) {
                p.setString(1, owner.email()); p.setString(2, "not-used"); p.setString(3, "Duplicate"); p.executeUpdate();
            }
        });
    }

    @Test
    void legacyRowsRemainStoredButInvisibleToRegisteredUsers() throws Exception {
        var owner = newAccount("legacy");
        long legacyId;
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO habits(name,description,category,frequency,color,created_at) VALUES (?,?,?,?,?,?) RETURNING id")) {
            p.setString(1, "Unassigned legacy " + UUID.randomUUID()); p.setString(2, "Old shared row");
            p.setString(3, "Mind"); p.setString(4, "Daily"); p.setString(5, "#5B5FEF");
            p.setDate(6, Date.valueOf(TODAY));
            try (var keys = p.executeQuery()) { assertTrue(keys.next()); legacyId = keys.getLong(1); }
        }
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO habit_checkins(habit_id,checkin_date,note) VALUES (?,?,?)")) {
            p.setLong(1, legacyId); p.setDate(2, Date.valueOf(TODAY)); p.setString(3, "legacy record"); p.executeUpdate();
        }
        assertTrue(habits.findAllWithProgress(owner.id(), TODAY).stream().noneMatch(h -> h.id() == legacyId));
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM habits WHERE id=? AND user_id IS NULL")) {
            p.setLong(1, legacyId);
            try (var r = p.executeQuery()) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
        }
    }

    @Test
    void crudNotesCheckinsPreferencesTargetsFocusAndRollbackWork() throws Exception {
        var owner = newAccount("crud");
        var other = newAccount("isolation");
        assertEquals("light", workspace.theme(owner.id()), "New accounts default to Light Mode");
        long id = habits.save(owner.id(), "Original", "Description", "Mind", "Daily", "#5B5FEF");
        habits.addCheckin(owner.id(), id, TODAY, "checked in");
        workspace.saveNote(owner.id(), id, TODAY, "saved note");
        workspace.saveMetadata(owner.id(), id, "Morning", 1, 2, TODAY);
        workspace.saveFocus(owner.id(), id, Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)), 5);
        workspace.saveTheme(owner.id(), "light");

        assertTrue(habits.update(owner.id(), id, "Updated", "New description", "Career", "Weekdays",
                "#2583D8", "Evening", 1, 3, TODAY));
        var updated = habits.findById(owner.id(), id).orElseThrow();
        assertEquals("Updated", updated.name());
        assertEquals("New description", updated.description());
        assertEquals("Career", updated.category());
        assertEquals("Weekdays", updated.frequency());
        assertEquals("#2583D8", updated.color());
        assertTrue(habits.hasCheckin(owner.id(), id, TODAY));
        Map<?, ?> data = workspace.load(owner.id());
        assertEquals("light", data.get("theme"));
        assertEquals(5, ((Map<?, ?>) data.get("focus")).get("totalMinutes"));
        Map<?, ?> meta = (Map<?, ?>) ((Map<?, ?>) data.get("metadata")).get(String.valueOf(id));
        assertEquals("saved note", ((Map<?, ?>) meta.get("notes")).get(TODAY.toString()));
        assertEquals("Evening", meta.get("time"));
        assertEquals(3, ((Map<?, ?>) meta.get("targets")).get(TODAY.toString()) instanceof Map<?, ?> target
                ? target.get("total") : -1);

        assertFalse(habits.update(other.id(), id, "Unauthorized", "", "Mind", "Daily", "#E11D48",
                "Anytime", 0, 1, TODAY));
        assertEquals("Updated", habits.findById(owner.id(), id).orElseThrow().name());
        assertTrue(habits.findById(other.id(), id).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> habits.update(owner.id(), id, "Should roll back", "",
                "Body", "Weekly", "#0FA77A", "Morning", 0, 0, TODAY));
        assertEquals("Updated", habits.findById(owner.id(), id).orElseThrow().name());

        habits.removeCheckin(owner.id(), id, TODAY);
        assertFalse(habits.hasCheckin(owner.id(), id, TODAY));
        habits.delete(owner.id(), id);
        assertTrue(habits.findById(owner.id(), id).isEmpty());
    }

    private static com.habitflow.model.UserAccount newAccount(String label) {
        String token = label + "-" + UUID.randomUUID().toString().replace("-", "");
        return auth.register(token + "@example.test", "IntegrationTestPassword123", label);
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set the local PostgreSQL test environment before using this profile.");
        return value;
    }

    private static void assertSqlState(String expected, org.junit.jupiter.api.function.Executable action) {
        SQLException failure = assertThrows(SQLException.class, action);
        assertEquals(expected, failure.getSQLState());
    }
}
