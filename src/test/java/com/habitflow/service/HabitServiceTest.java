package com.habitflow.service;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.dao.HabitDao;
import com.habitflow.model.Habit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Testing and collections: this test checks dashboard totals using a habit list.
class HabitServiceTest {
    @Test
    void calculatesDashboardStatsFromHabitCollection() {
        HabitDao unusedDao = new HabitDao() {
            public List<Habit> findAllWithProgress(LocalDate today) { return List.of(); }
            public java.util.Optional<Habit> findById(long id) { return java.util.Optional.empty(); }
            public long save(String name, String description, String category, String frequency, String color) { return 0; }
            public void delete(long id) { }
            public boolean hasCheckin(long habitId, LocalDate date) { return false; }
            public void addCheckin(long habitId, LocalDate date, String note) { }
            public void removeCheckin(long habitId, LocalDate date) { }
        };
        try (HabitService service = new HabitService(unusedDao)) {
            List<Habit> habits = List.of(
                    new Habit(1, "Read", "", "Mind", "Daily", "#fff", LocalDate.now(), true, 8, 80),
                    new Habit(2, "Walk", "", "Body", "Daily", "#fff", LocalDate.now(), false, 12, 90)
            );

            assertEquals(2, service.getStats(habits).totalHabits());
            assertEquals(1, service.getStats(habits).completedToday());
            assertEquals(12, service.getStats(habits).bestStreak());
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("  ", "", "Mind", "Daily", "#5B5FEF"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.toggleToday(1, "x".repeat(251)));
        }
    }
}
