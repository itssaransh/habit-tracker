package com.habitflow.dao;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.config.Database;
import com.habitflow.model.Habit;
import com.habitflow.model.HabitMetrics;
import com.habitflow.model.HabitScheduleFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// JDBC: prepared statements run the SQL, and collections group check-in dates.
public class JdbcHabitDao implements HabitDao {
    @Override
    public List<Habit> findAllWithProgress(LocalDate today) {
        String sql = """
                SELECT id, name, description, category, frequency, color, created_at
                FROM habits ORDER BY created_at DESC, id DESC
                """;
        List<Habit> habits = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Map<Long, Set<LocalDate>> checkInsByHabit = loadCheckIns(connection);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = result.getLong("id");
                    LocalDate createdAt = result.getDate("created_at").toLocalDate();
                    String frequency = result.getString("frequency");
                    Set<LocalDate> checkIns = checkInsByHabit.getOrDefault(id, Set.of());
                    HabitMetrics metrics = HabitMetrics.calculate(
                            HabitScheduleFactory.fromFrequency(frequency), createdAt, checkIns, today);
                    habits.add(new Habit(
                            id, result.getString("name"),
                            result.getString("description"), result.getString("category"),
                            frequency, result.getString("color"), createdAt,
                            checkIns.contains(today), metrics.currentStreak(), metrics.bestStreak(),
                            metrics.weeklyCheckIns(), metrics.completionRate()
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load habits", exception);
        }
        return habits;
    }

    private Map<Long, Set<LocalDate>> loadCheckIns(Connection connection) throws SQLException {
        Map<Long, Set<LocalDate>> checkInsByHabit = new HashMap<>();
        String sql = "SELECT habit_id, checkin_date FROM habit_checkins ORDER BY checkin_date";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long habitId = result.getLong("habit_id");
                LocalDate date = result.getDate("checkin_date").toLocalDate();
                checkInsByHabit.computeIfAbsent(habitId, ignored -> new HashSet<>()).add(date);
            }
        }
        return checkInsByHabit;
    }

    @Override
    public Optional<Habit> findById(long id) {
        return findAllWithProgress(LocalDate.now()).stream()
                .filter(habit -> habit.id() == id).findFirst();
    }

    @Override
    public long save(String name, String description, String category, String frequency, String color) {
        String sql = "INSERT INTO habits(name, description, category, frequency, color, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setString(3, category);
            statement.setString(4, frequency);
            statement.setString(5, color);
            statement.setDate(6, Date.valueOf(LocalDate.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save habit", exception);
        }
        throw new IllegalStateException("Database did not return a habit id");
    }

    @Override
    public void delete(long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM habits WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete habit", exception);
        }
    }

    @Override
    public boolean hasCheckin(long habitId, LocalDate date) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM habit_checkins WHERE habit_id = ? AND checkin_date = ?")) {
            statement.setLong(1, habitId);
            statement.setDate(2, Date.valueOf(date));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check habit progress", exception);
        }
    }

    @Override
    public void addCheckin(long habitId, LocalDate date, String note) {
        String sql = Database.isPostgres()
                ? "INSERT INTO habit_checkins(habit_id, checkin_date, note) VALUES (?, ?, ?) "
                    + "ON CONFLICT (habit_id, checkin_date) DO UPDATE SET note = EXCLUDED.note"
                : "MERGE INTO habit_checkins(habit_id, checkin_date, note) KEY(habit_id, checkin_date) VALUES (?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, habitId);
            statement.setDate(2, Date.valueOf(date));
            statement.setString(3, note);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to record check-in", exception);
        }
    }

    @Override
    public void removeCheckin(long habitId, LocalDate date) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM habit_checkins WHERE habit_id = ? AND checkin_date = ?")) {
            statement.setLong(1, habitId);
            statement.setDate(2, Date.valueOf(date));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to undo check-in", exception);
        }
    }
}
