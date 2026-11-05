package com.habitflow.model;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

// Collections: a set of check-in dates is used to calculate habit progress.
public record HabitMetrics(int currentStreak, int bestStreak, int completionRate, int weeklyCheckIns) {
    public static HabitMetrics calculate(HabitSchedule schedule, LocalDate createdAt,
                                         Set<LocalDate> checkIns, LocalDate today) {
        int current = currentStreak(schedule, createdAt, checkIns, today);
        int best = bestStreak(schedule, createdAt, checkIns, today);
        int rate = completionRate(schedule, createdAt, checkIns, today);
        int weekly = weeklyCheckIns(schedule, createdAt, checkIns, today);
        return new HabitMetrics(current, best, rate, weekly);
    }

    private static int currentStreak(HabitSchedule schedule, LocalDate createdAt,
                                     Set<LocalDate> checkIns, LocalDate today) {
        if (schedule.isScheduledOn(today, createdAt) && !checkIns.contains(today)) return 0;
        LocalDate cursor = checkIns.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (!cursor.isBefore(createdAt)) {
            if (schedule.isScheduledOn(cursor, createdAt)) {
                if (!checkIns.contains(cursor)) break;
                streak++;
            }
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static int bestStreak(HabitSchedule schedule, LocalDate createdAt,
                                  Set<LocalDate> checkIns, LocalDate today) {
        int best = 0;
        int streak = 0;
        for (LocalDate date = createdAt; !date.isAfter(today); date = date.plusDays(1)) {
            if (!schedule.isScheduledOn(date, createdAt)) continue;
            if (checkIns.contains(date)) {
                streak++;
                best = Math.max(best, streak);
            } else {
                streak = 0;
            }
        }
        return best;
    }

    private static int completionRate(HabitSchedule schedule, LocalDate createdAt,
                                      Set<LocalDate> checkIns, LocalDate today) {
        LocalDate firstDay = today.minusDays(29).isBefore(createdAt) ? createdAt : today.minusDays(29);
        int dueDays = 0;
        int completedDays = 0;
        for (LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {
            if (!schedule.isScheduledOn(date, createdAt)) continue;
            dueDays++;
            if (checkIns.contains(date)) completedDays++;
        }
        return dueDays == 0 ? 0 : Math.min(100, Math.round(completedDays * 100f / dueDays));
    }

    private static int weeklyCheckIns(HabitSchedule schedule, LocalDate createdAt,
                                      Set<LocalDate> checkIns, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int count = 0;
        for (LocalDate date = monday.isBefore(createdAt) ? createdAt : monday;
             !date.isAfter(today); date = date.plusDays(1)) {
            if (schedule.isScheduledOn(date, createdAt) && checkIns.contains(date)) count++;
        }
        return count;
    }
}
