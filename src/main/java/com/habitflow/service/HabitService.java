package com.habitflow.service;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.dao.HabitDao;
import com.habitflow.model.Habit;
import com.habitflow.model.HabitScheduleFactory;
import com.habitflow.model.HabitStats;

import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Collections and validation: the service checks input and works with habit lists.
public class HabitService implements AutoCloseable {
    private static final Set<String> CATEGORIES = Set.of("Mind", "Body", "Career", "Wellness", "Learning");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private final HabitDao habitDao;
    private final ExecutorService analyticsExecutor = Executors.newFixedThreadPool(2);
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock(true);

    public HabitService(HabitDao habitDao) {
        this.habitDao = Objects.requireNonNull(habitDao);
    }

    public List<Habit> getHabits() {
        stateLock.readLock().lock();
        try {
            return List.copyOf(habitDao.findAllWithProgress(LocalDate.now()));
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public HabitStats getStats(List<Habit> habits) {
        stateLock.readLock().lock();
        try {
            int completed = (int) habits.stream().filter(Habit::completedToday).count();
            int bestStreak = habits.stream().mapToInt(Habit::bestStreak).max().orElse(0);
            int weeklyCompletions = habits.stream().mapToInt(Habit::weeklyCheckIns).sum();
            return new HabitStats(habits.size(), completed, bestStreak, weeklyCompletions);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public CompletableFuture<HabitStats> getStatsAsync(List<Habit> habits) {
        return CompletableFuture.supplyAsync(() -> getStats(habits), analyticsExecutor);
    }

    public void create(String name, String description, String category, String frequency, String color) {
        String cleanName = requireText(name, "Habit name");
        if (cleanName.length() > 120) throw new HabitValidationException("Habit name must be 120 characters or fewer");
        String cleanDescription = cleanText(description);
        if (cleanDescription.length() > 500) {
            throw new HabitValidationException("Description must be 500 characters or fewer");
        }
        String cleanCategory = requireText(category, "Category");
        if (!CATEGORIES.contains(cleanCategory)) throw new HabitValidationException("Choose a listed category");
        String cleanFrequency = normalizeFrequency(frequency);
        String cleanColor = requireText(color, "Color");
        if (!HEX_COLOR.matcher(cleanColor).matches()) throw new HabitValidationException("Choose a valid accent color");

        stateLock.writeLock().lock();
        try {
            habitDao.save(cleanName, cleanDescription, cleanCategory, cleanFrequency, cleanColor.toUpperCase(Locale.ROOT));
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public void toggleToday(long habitId, String note) {
        LocalDate today = LocalDate.now();
        String cleanNote = cleanText(note);
        if (cleanNote.length() > 250) {
            throw new HabitValidationException("Check-in note must be 250 characters or fewer");
        }
        stateLock.writeLock().lock();
        try {
            Habit habit = habitDao.findById(habitId)
                    .orElseThrow(() -> new HabitValidationException("That habit could not be found"));
            if (!habit.isScheduledOn(today)) {
                throw new HabitValidationException("This habit is not scheduled for today");
            }
            if (habitDao.hasCheckin(habitId, today)) {
                habitDao.removeCheckin(habitId, today);
            } else {
                habitDao.addCheckin(habitId, today, cleanNote);
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public void delete(long habitId) {
        stateLock.writeLock().lock();
        try {
            habitDao.delete(habitId);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new HabitValidationException(field + " is required");
        }
        return value.trim();
    }

    private String normalizeFrequency(String value) {
        String frequency = requireText(value, "Frequency");
        try {
            HabitScheduleFactory.fromFrequency(frequency);
        } catch (IllegalArgumentException exception) {
            throw new HabitValidationException(exception.getMessage());
        }
        return switch (frequency.toLowerCase(Locale.ROOT)) {
            case "daily" -> "Daily";
            case "weekdays" -> "Weekdays";
            case "weekly" -> "Weekly";
            default -> throw new HabitValidationException("Choose Daily, Weekdays, or Weekly frequency");
        };
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public void close() {
        analyticsExecutor.shutdown();
    }
}
