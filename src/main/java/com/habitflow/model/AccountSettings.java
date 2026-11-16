package com.habitflow.model;

import java.time.LocalTime;

public record AccountSettings(String fullName, String workspaceName, LocalTime dailyReminderTime,
                              int weeklyGoalDays) {
    public String getFullName() { return fullName; }
    public String getWorkspaceName() { return workspaceName; }
    public LocalTime getDailyReminderTime() { return dailyReminderTime; }
    public int getWeeklyGoalDays() { return weeklyGoalDays; }
}
