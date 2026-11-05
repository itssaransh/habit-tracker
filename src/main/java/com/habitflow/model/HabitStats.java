package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

// OOP: this record stores the totals shown on the dashboard.
public record HabitStats(int totalHabits, int completedToday, int bestStreak, int weeklyCompletions) {
    // JavaBean getters let JSP read these dashboard totals.
    public int getTotalHabits() { return totalHabits; }
    public int getCompletedToday() { return completedToday; }
    public int getBestStreak() { return bestStreak; }
    public int getWeeklyCompletions() { return weeklyCompletions; }
}
