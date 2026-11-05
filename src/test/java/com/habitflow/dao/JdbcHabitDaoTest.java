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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcHabitDaoTest {
    private final JdbcHabitDao dao = new JdbcHabitDao();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void createTestDatabase() {
        System.setProperty("habit.tracker.environment", "test");
        System.setProperty("habit.tracker.jdbc.url",
                "jdbc:h2:mem:habit_tracker_" + UUID.randomUUID().toString().replace("-", "")
                        + ";DB_CLOSE_DELAY=-1");
        System.setProperty("habit.tracker.jdbc.user", "sa");
        System.setProperty("habit.tracker.jdbc.password", "");
        Database.initialize();
    }

    @Test
    void savesListsChecksInAndDeletesHabits() throws Exception {
        long id = dao.save("Read", "Read one chapter", "Learning", "Daily", "#5B5FEF");

        Habit saved = dao.findById(id).orElseThrow();
        assertEquals("Read", saved.name());
        assertFalse(saved.completedToday());

        dao.addCheckin(id, today, "Chapter one");
        dao.addCheckin(id, today, "Finished chapter two");
        assertTrue(dao.hasCheckin(id, today));
        assertTrue(dao.findById(id).orElseThrow().completedToday());
        assertEquals("Finished chapter two", findCheckinNote(id));

        dao.removeCheckin(id, today);
        assertFalse(dao.hasCheckin(id, today));
        dao.addCheckin(id, today, "Done");
        dao.delete(id);
        assertTrue(dao.findById(id).isEmpty());
        assertFalse(dao.hasCheckin(id, today));
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
