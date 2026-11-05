package com.habitflow.dao;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.model.Habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// OOP: this interface lists the database operations used by the service.
public interface HabitDao {
    List<Habit> findAllWithProgress(LocalDate today);
    Optional<Habit> findById(long id);
    long save(String name, String description, String category, String frequency, String color);
    void delete(long id);
    boolean hasCheckin(long habitId, LocalDate date);
    void addCheckin(long habitId, LocalDate date, String note);
    void removeCheckin(long habitId, LocalDate date);
}
