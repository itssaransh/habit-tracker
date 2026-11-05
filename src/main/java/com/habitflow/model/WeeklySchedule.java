package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.LocalDate;

// OOP: a weekly habit repeats on the weekday when it was created.
public final class WeeklySchedule extends HabitSchedule {
    @Override
    public boolean isScheduledOn(LocalDate date, LocalDate createdAt) {
        return !date.isBefore(createdAt) && date.getDayOfWeek() == createdAt.getDayOfWeek();
    }
}
