package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.LocalDate;

// OOP: each schedule subclass provides its own due-day rule.
public abstract class HabitSchedule {
    public abstract boolean isScheduledOn(LocalDate date, LocalDate createdAt);
}
