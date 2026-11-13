package com.habitflow.dao;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).
// Ayush Sharma: these tests check the DAO with separate H2 databases.
import com.habitflow.config.Database;
import com.habitflow.model.Habit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcHabitDaoTest {
    private final JdbcHabitDao dao = new JdbcHabitDao();
    private final LocalDate today = LocalDate.now();
    private long alice;
    private long bob;

    @BeforeEach
    void createTestDatabase() {
        System.setProperty("habit.tracker.environment", "test");
        System.setProperty("habit.tracker.jdbc.url",
                "jdbc:h2:mem:habit_tracker_" + UUID.randomUUID().toString().replace("-", "")
                        + ";DB_CLOSE_DELAY=-1");
        System.setProperty("habit.tracker.jdbc.user", "sa");
        System.setProperty("habit.tracker.jdbc.password", "");
        Database.initialize();
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO users(email,password_hash,display_name) VALUES (?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,"alice@example.test");p.setString(2,"hash");p.setString(3,"Alice");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();alice=r.getLong(1);}
        } catch(Exception e){throw new IllegalStateException(e);}
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO users(email,password_hash,display_name) VALUES (?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,"bob@example.test");p.setString(2,"hash");p.setString(3,"Bob");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();bob=r.getLong(1);}
        } catch(Exception e){throw new IllegalStateException(e);}
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO user_preferences(user_id) VALUES (?)")){p.setLong(1,alice);p.executeUpdate();p.setLong(1,bob);p.executeUpdate();}catch(Exception e){throw new IllegalStateException(e);}
    }

    @Test
    void savesListsChecksInAndDeletesHabits() throws Exception {
        long id = dao.save(alice, "Read", "Read one chapter", "Learning", "Daily", "#5B5FEF");

        Habit saved = dao.findById(alice,id).orElseThrow();
        assertEquals("Read", saved.name());
        assertFalse(saved.completedToday());

        dao.addCheckin(alice,id, today, "Chapter one");
        dao.addCheckin(alice,id, today, "Finished chapter two");
        assertTrue(dao.hasCheckin(alice,id, today));
        assertTrue(dao.findById(alice,id).orElseThrow().completedToday());
        assertTrue(dao.findById(bob,id).isEmpty());
        assertTrue(dao.findAllWithProgress(bob,today).isEmpty());
        dao.delete(bob,id);
        assertTrue(dao.findById(alice,id).isPresent());
        assertEquals("Finished chapter two", findCheckinNote(id));

        dao.removeCheckin(alice,id, today);
        assertFalse(dao.hasCheckin(alice,id, today));
        dao.addCheckin(alice,id, today, "Done");
        dao.delete(alice,id);
        assertTrue(dao.findById(alice,id).isEmpty());
        assertFalse(dao.hasCheckin(alice,id, today));
    }

    @Test
    void legacySharedRowsStayStoredButAreNotShownToNewAccounts() throws Exception {
        long legacyId;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(
                "INSERT INTO habits(name,description,category,frequency,color,created_at) VALUES ('Shared','old data','Mind','Daily','#5B5FEF',?)",java.sql.Statement.RETURN_GENERATED_KEYS)) {
            p.setDate(1,java.sql.Date.valueOf(today));p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();legacyId=r.getLong(1);}
        }
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO habit_checkins(habit_id,checkin_date,note) VALUES (?,?,?)")) {
            p.setLong(1,legacyId);p.setDate(2,java.sql.Date.valueOf(today));p.setString(3,"legacy note");p.executeUpdate();
        }
        Database.initialize();
        assertTrue(dao.findAllWithProgress(alice,today).isEmpty());
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM habits WHERE id=?")) {
            p.setLong(1,legacyId);try(ResultSet r=p.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
        }
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM habit_checkins WHERE habit_id=?")) {
            p.setLong(1,legacyId);try(ResultSet r=p.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
        }
    }

    @Test
    void updatesOwnedHabitAndSettingsWithoutChangingHistory() throws Exception {
        long id = dao.save(alice, "Read", "Before edit", "Learning", "Daily", "#5B5FEF");
        dao.addCheckin(alice, id, today, "Keep this check-in");
        JdbcWorkspaceDao workspace = new JdbcWorkspaceDao();
        workspace.saveNote(alice, id, today, "Keep this note");

        assertTrue(dao.update(alice, id, "Study", "After edit", "Career", "Weekdays",
                "#2583D8", "Evening", 1, 3, today));
        Habit edited = dao.findById(alice, id).orElseThrow();
        assertEquals("Study", edited.name());
        assertEquals("After edit", edited.description());
        assertEquals("Career", edited.category());
        assertEquals("Weekdays", edited.frequency());
        assertEquals("#2583D8", edited.color());
        assertTrue(dao.hasCheckin(alice, id, today));
        assertEquals("Keep this check-in", findCheckinNote(id));
        Map<?, ?> savedMetadata = (Map<?, ?>) ((Map<?, ?>) workspace.load(alice).get("metadata")).get(String.valueOf(id));
        assertEquals("Evening", savedMetadata.get("time"));
        assertEquals("Keep this note", ((Map<?, ?>) savedMetadata.get("notes")).get(today.toString()));
        assertEquals(3, ((Map<?, ?>) savedMetadata.get("targets")).get(today.toString()) instanceof Map<?, ?> target
                ? target.get("total") : -1);

        assertFalse(dao.update(bob, id, "Stolen", "", "Mind", "Daily", "#E11D48",
                "Morning", 0, 1, today));
        assertEquals("Study", dao.findById(alice, id).orElseThrow().name());
        assertTrue(dao.findById(bob, id).isEmpty());
    }

    @Test
    void failedSettingsWriteRollsBackHabitUpdate() {
        long id = dao.save(alice, "Original", "", "Mind", "Daily", "#5B5FEF");
        assertThrows(IllegalArgumentException.class, () -> dao.update(alice, id, "Should roll back", "",
                "Body", "Weekly", "#2583D8", "Morning", 1, 0, today));
        assertEquals("Original", dao.findById(alice, id).orElseThrow().name());
        assertEquals("Mind", dao.findById(alice, id).orElseThrow().category());
    }

    private String findCheckinNote(long habitId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT note FROM habit_checkins WHERE habit_id = ? AND checkin_date = ?")) {
            statement.setLong(1, habitId);
            statement.setDate(2, java.sql.Date.valueOf(today));
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("note");
            }
        }
    }
}
