# Personal Habit Tracker - Project Report

## Introduction

Personal Habit Tracker (HabitFlow) is a Java web app for saving private habits and recording which days they are completed. The dashboard shows a signed-in user's streaks and progress.

## Problem

People can forget which habits they completed or how regularly they followed a routine. This app keeps the habit list and check-in dates in a database so that progress can be reviewed later.

## Objectives

- Add habits with a category and repeat schedule.
- Save one check-in for a habit on each date.
- Register and log in with a BCrypt password hash stored in the configured database.
- Keep account data private using server-side sessions and user ownership in database queries.
- Save notes, check-ins, daily targets, focus sessions, and preferences through the application API to the configured database.
- Show current streak, best streak, weekly check-ins, and completion rate.
- Practise Java OOP, collections, exceptions, JDBC, Servlets, and JSP.

## Tools and technologies

- Java 17 and Maven
- Jakarta Servlets 6.0 and JSP 3.1
- Apache Tomcat 10.1
- JDBC with H2 for local work and PostgreSQL for online hosting
- HTML, CSS, JavaScript, and JSTL

## Project structure

Servlet classes receive browser requests. `SecurityFilter` requires a signed-in server session and checks CSRF tokens for write requests. `DashboardServlet` loads only the signed-in user's habits and totals, then forwards them to `index.jsp`. `AuthService` validates credentials and uses BCrypt. `HabitService` checks habit form values. The DAOs run prepared SQL with the session owner's ID. `Database` opens connections and applies versioned migrations when the app starts.

The schedule classes represent daily, weekday, and weekly habits. `HabitScheduleFactory` selects a schedule based on the frequency saved for a habit. `HabitMetrics` calculates streaks and completion rates from check-in dates.

## Database

The database has account, habit, check-in, preference, note, daily target, and focus-session tables. User-owned rows contain an account ID, and foreign keys associate them with that account's habits. A unique key on a habit and date stops duplicate check-ins. Old unowned shared records remain in the database but are hidden from new accounts.

For local use, H2 saves data to a file in Tomcat's `data` folder. For hosting, the app connects to PostgreSQL using environment variables so that saved data does not depend on the web server's temporary disk.

## Main user steps

1. Register an account or sign in.
2. Add a habit and choose a category and schedule.
3. Mark the habit complete when it is due and save notes or focus sessions.
4. Sign in to the same account again to load its saved workspace. Access from a separate physical device is intended by the server-backed design but has not been verified for this release.
5. Sign out before switching to another account.

## Team members

- **Shaswat Gupta:** OOP models: `Habit.java`, `HabitMetrics.java`, `HabitStats.java`, `HabitSchedule.java`, `DailySchedule.java`, `WeekdaySchedule.java`, `WeeklySchedule.java`, and `HabitScheduleFactory.java`.
- **Saransh Mishra:** Database schema (`schema.sql`), JDBC configuration (`Database.java`, `AppContextListener.java`), and DAO classes (`HabitDao.java`, `JdbcHabitDao.java`).
- **Ayush Sharma:** Service validation, exception handling, collections, and tests (`HabitService.java`, `HabitValidationException.java`, `HabitServiceTest.java`).
- **Shivansh Singh:** Servlets and web UI (`HabitServlet.java`, `DashboardServlet.java`, `CheckInServlet.java`, `HealthServlet.java`, `index.jsp`, `styles.css`, `app.js`).

## Scope and limitations

Password reset, email verification, and session/device history are not implemented. The account screen does not claim those features. The recorded local verification is a Java 17 build with 9 unit tests and 3 PostgreSQL integration tests against an isolated PostgreSQL 18.6 database, plus local Tomcat testing. The production Neon schema, production deployment, live URL for this release, and physical-device synchronization have not been verified. Before a production deployment, confirm a Neon recovery point and review the migrations against a safe copy of the existing schema. Legacy shared records remain unassigned and hidden from new accounts.

## Conclusion

This project combines a Java web interface with a relational database. It gave the team practice with Java classes, schedule rules, collections, input validation, JDBC queries, Servlets, and JSP.
