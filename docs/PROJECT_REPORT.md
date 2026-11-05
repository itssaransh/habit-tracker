# Personal Habit Tracker - Project Report

## Introduction

Personal Habit Tracker is a Java web app for saving habits and recording which days they are completed. The dashboard shows a user's streaks and progress. HabitFlow is the name shown on the page.

## Problem

People can forget which habits they completed or how regularly they followed a routine. This app keeps the habit list and check-in dates in a database so that progress can be reviewed later.

## Objectives

- Add habits with a category and repeat schedule.
- Save one check-in for a habit on each date.
- Show current streak, best streak, weekly check-ins, and completion rate.
- Practise Java OOP, collections, exceptions, JDBC, Servlets, and JSP.

## Tools and technologies

- Java 17 and Maven
- Jakarta Servlets 6.0 and JSP 3.1
- Apache Tomcat 10.1
- JDBC with H2 for local work and PostgreSQL for online hosting
- HTML, CSS, JavaScript, and JSTL

## Project structure

Servlet classes receive browser requests. `DashboardServlet` loads the habit list and dashboard totals, then forwards them to `index.jsp`. `HabitService` checks form values and coordinates the operations. `HabitDao` defines the database work, and `JdbcHabitDao` runs SQL with prepared statements. `Database` opens connections and applies the schema when the app starts.

The schedule classes represent daily, weekday, and weekly habits. `HabitScheduleFactory` selects a schedule based on the frequency saved for a habit. `HabitMetrics` calculates streaks and completion rates from check-in dates.

## Database

The app has a `habits` table and a `habit_checkins` table. Each check-in belongs to one habit. A unique key on the habit and date stops duplicate check-ins. Deleting a habit also deletes its check-ins.

For local use, H2 saves data to a file in Tomcat's `data` folder. For hosting, the app connects to PostgreSQL using environment variables so that saved data does not depend on the web server's temporary disk.

## Main user steps

1. Open the dashboard.
2. Add a habit and choose a category and schedule.
3. Mark the habit complete when it is due.
4. Review streaks and weekly progress.
5. Undo a check-in or delete a habit when needed.

## Team members

- **Shaswat Gupta:** OOP models: `Habit.java`, `HabitMetrics.java`, `HabitStats.java`, `HabitSchedule.java`, `DailySchedule.java`, `WeekdaySchedule.java`, `WeeklySchedule.java`, and `HabitScheduleFactory.java`.
- **Saransh Mishra:** Database schema (`schema.sql`), JDBC configuration (`Database.java`, `AppContextListener.java`), and DAO classes (`HabitDao.java`, `JdbcHabitDao.java`).
- **Ayush Sharma:** Service validation, exception handling, collections, and tests (`HabitService.java`, `HabitValidationException.java`, `HabitServiceTest.java`).
- **Shivansh Singh:** Servlets and web UI (`HabitServlet.java`, `DashboardServlet.java`, `CheckInServlet.java`, `HealthServlet.java`, `index.jsp`, `styles.css`, `app.js`).

## Scope and limitations

The app does not have user accounts. If it is deployed publicly, everyone visiting it sees and can change the same habit list. It is suitable as a class project demo, not for storing private information. Render's free web service can sleep when idle, while the separate PostgreSQL database keeps the saved records.

## Conclusion

This project combines a Java web interface with a relational database. It gave the team practice with Java classes, schedule rules, collections, input validation, JDBC queries, Servlets, and JSP.
