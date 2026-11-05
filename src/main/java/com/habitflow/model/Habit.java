package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.LocalDate;

// OOP: this record stores a habit and its current progress.
public record Habit(
        long id,
        String name,
        String description,
        String category,
        String frequency,
        String color,
        LocalDate createdAt,
        boolean completedToday,
        int currentStreak,
        int bestStreak,
        int weeklyCheckIns,
        int completionRate
) {

    // Short constructor for simple in-memory examples.
    public Habit(long id, String name, String description, String category, String frequency,
                 String color, LocalDate createdAt, boolean completedToday,
                 int currentStreak, int completionRate) {
        this(id, name, description, category, frequency, color, createdAt, completedToday,
                currentStreak, currentStreak, 0, completionRate);
    }

    public boolean isScheduledOn(LocalDate date) {
        return HabitScheduleFactory.fromFrequency(frequency).isScheduledOn(date, createdAt);
    }

    public boolean isScheduledToday() {
        return isScheduledOn(LocalDate.now());
    }

    // JavaBean getters keep the record values available to JSP Expression Language.
    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getFrequency() { return frequency; }
    public String getColor() { return color; }
    public LocalDate getCreatedAt() { return createdAt; }
    public boolean getCompletedToday() { return completedToday; }
    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public int getWeeklyCheckIns() { return weeklyCheckIns; }
    public int getCompletionRate() { return completionRate; }
}
