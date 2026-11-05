package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.LocalDate;

// OOP: this schedule makes a habit due on every day after it was created.
public final class DailySchedule extends HabitSchedule {
    @Override
    public boolean isScheduledOn(LocalDate date, LocalDate createdAt) {
        return !date.isBefore(createdAt);
    }
}
