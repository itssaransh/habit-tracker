package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.DayOfWeek;
import java.time.LocalDate;

// OOP: this schedule skips Saturday and Sunday.
public final class WeekdaySchedule extends HabitSchedule {
    @Override
    public boolean isScheduledOn(LocalDate date, LocalDate createdAt) {
        DayOfWeek day = date.getDayOfWeek();
        return !date.isBefore(createdAt) && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
