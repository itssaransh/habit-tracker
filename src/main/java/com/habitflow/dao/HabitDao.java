package com.habitflow.dao;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.model.Habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// OOP: this interface lists the database operations used by the service.
public interface HabitDao {
    List<Habit> findAllWithProgress(long userId, LocalDate today);
    Optional<Habit> findById(long userId, long id);
    long save(long userId, String name, String description, String category, String frequency, String color);
    boolean update(long userId, long id, String name, String description, String category, String frequency,
                   String color, String timeOfDay, int targetCount, int targetTotal, LocalDate targetDate);
    void delete(long userId, long id);
    boolean hasCheckin(long userId, long habitId, LocalDate date);
    void addCheckin(long userId, long habitId, LocalDate date, String note);
    void removeCheckin(long userId, long habitId, LocalDate date);
}
