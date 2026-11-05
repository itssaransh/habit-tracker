package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.util.Locale;

// OOP: the factory selects a schedule class from the frequency.
public final class HabitScheduleFactory {
    private HabitScheduleFactory() { }

    public static HabitSchedule fromFrequency(String frequency) {
        if (frequency == null) throw new IllegalArgumentException("Frequency is required");
        return switch (frequency.trim().toLowerCase(Locale.ROOT)) {
            case "daily" -> new DailySchedule();
            case "weekdays" -> new WeekdaySchedule();
            case "weekly" -> new WeeklySchedule();
            default -> throw new IllegalArgumentException("Choose Daily, Weekdays, or Weekly frequency");
        };
    }
}
