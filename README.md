# Personal Habit Tracker

> **[Open the live demo](https://personal-habit-tracker-3vv1.onrender.com/)**

**Live website:** [https://personal-habit-tracker-3vv1.onrender.com/](https://personal-habit-tracker-3vv1.onrender.com/)

Personal Habit Tracker (HabitFlow) is a Java web app for creating a private routine, recording check-ins, and reviewing progress. Each account is identified by a server-side session, and its workspace is loaded and saved through the Java application and configured database. PostgreSQL is intended for production; this release has only been checked against an isolated local database.

This is a Java subject project built with Servlets, JSP, JDBC, and Maven.

## Features

- Add a habit with a category, schedule, description, and accent color.
- Edit a habit's supported details while retaining its check-in history.
- Choose a daily, weekday, or weekly schedule.
- Record or undo today's check-in.
- See current and best streaks, weekly check-ins, and completion rate.
- Delete habits and their check-in history.
- Register, sign in, and sign out with BCrypt password hashes and CSRF-protected forms.
- Keep habits, check-ins, notes, goals, focus sessions, profile name, and theme scoped to the signed-in account.
- Start in Light Mode by default, with an optional Dark Mode toggle.
- Save workspace changes through the Java API to the configured database.
- Use a local H2 database or a hosted PostgreSQL database.

## Technology

- Java 17
- Maven
- Jakarta Servlet 6.0 and JSP 3.1
- Apache Tomcat 10.1
- JDBC
- BCrypt password hashing
- Gson JSON API
- H2 for local runs; PostgreSQL for deployment
- HTML, CSS, JavaScript, and JSTL

## Run locally

Install JDK 17. The Maven Wrapper downloads Maven 3.9.16 when needed and runs the build and tests:

```sh
.\mvnw.cmd clean verify
```

This creates `target/habitflow.war`. Copy it into the `webapps` folder of Tomcat 10.1 and start Tomcat. Open `http://localhost:8080/habitflow/`.

By default, the app creates an H2 database under Tomcat's `data/habitflow` folder. The database file stays on disk between restarts. Versioned migrations create the account and per-user data tables when the app starts. You can choose another folder with `APP_DATA_DIR`. For example, set it to `D:/habit-tracker-data` on Windows or `/var/lib/habit-tracker` on Linux before starting Tomcat.

For a local Docker image, run:

```sh
docker build -t personal-habit-tracker .
docker run --rm -p 10000:10000 -e PORT=10000 -e APP_DATA_DIR=/data -v habit-tracker-data:/data personal-habit-tracker
```

Open `http://localhost:10000/` after the container starts.

## Database configuration

Local development uses H2 and does not need a database account. The baseline schema is in `src/main/resources/schema.sql`. Additive migrations in `src/main/resources/db/migration/` are tracked in the `schema_migrations` table and run when the app starts.

Production mode requires PostgreSQL. Set these environment variables on the hosting service:

| Variable | Value |
| --- | --- |
| `APP_ENV` | `production` |
| `JDBC_URL` | `jdbc:postgresql://HOST/DATABASE?sslmode=require&prepareThreshold=0` |
| `JDBC_USER` | PostgreSQL role name |
| `JDBC_PASSWORD` | PostgreSQL role password |

The database password belongs in the hosting provider's secret settings, not in this repository. `prepareThreshold=0` is set when using a hosted connection pooler.

The recorded PostgreSQL integration tests used a separate local test database, not Neon or Render. To run them locally, set `HABITFLOW_TEST_JDBC_URL`, `HABITFLOW_TEST_JDBC_USER`, and `HABITFLOW_TEST_JDBC_PASSWORD` in the current PowerShell session for an isolated database, then run:

```powershell
.\mvnw.cmd -Ppostgres-integration clean verify
```

Never point this test profile at a production database.

## Deploy on Render and Neon

The repository includes a Dockerfile and `render.yaml` for a Render Docker web service. The container builds a Java 17 WAR and runs it on Tomcat 10.1. Render uses the `/health` endpoint to check that the app is ready.
The hosted app uses the India time zone for its daily check-ins.

The repository already describes the `personal-habit-tracker` Render service on `main`; use that existing service rather than creating a duplicate.

The existing service configuration requires `APP_ENV=production`, `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata`, and the existing secret values for `JDBC_URL`, `JDBC_USER`, and `JDBC_PASSWORD`. Render supplies `PORT`. Keep secret values in the existing Render environment settings; never commit them or paste them into repository files. Do not create a new Blueprint or service for this update.

The app applies versioned database migrations during startup. Confirm a PostgreSQL recovery point and review the migrations against a safe copy of the existing schema before allowing a deployment to start the update.

Merging to the service's watched `main` branch can trigger a deploy if Auto-Deploy is enabled. Check the service's setting before merging; if it is disabled, a deploy must be started manually.

## Account and data notes

New accounts only see habits and records created for their own account. Existing rows from the earlier shared workspace have no reliable owner, so migrations keep them intact but unassigned and hidden. No production demo users are seeded. The profile screen states that device history, email verification, and password reset are not available.

The recorded local verification is Java 17, 9 unit tests, 3 PostgreSQL integration tests against an isolated PostgreSQL 18.6 database, and local Tomcat testing. Production Neon, the current live URL, and physical-device synchronization have not been verified for this release. The live demo link above may still serve the older application until a separately approved release is deployed.

## Project folders

```text
src/main/java/com/habitflow/
  config/       Database connection, migrations and startup listener
  dao/          Database interfaces and JDBC implementations
  model/        Habit data, schedules, and progress calculations
  service/      Authentication, input validation and habit operations
  servlet/      Dashboard, authentication, account and form request handlers
  web/          Session and CSRF security filter
src/main/resources/schema.sql
src/main/resources/db/migration/   Versioned additive migrations
src/main/webapp/              JSP pages, CSS, and JavaScript
src/test/java/                Service, JDBC and security tests
docs/                         Project report and database design
```

## Team members

- **Shaswat Gupta:** OOP models and schedule classes.
- **Saransh Mishra:** Database schema, JDBC configuration, and DAO classes.
- **Ayush Sharma:** Service validation, exception handling, collections, and tests.
- **Shivansh Singh:** Servlets and web UI.

## Project documents

- [Project report](docs/PROJECT_REPORT.md)
- [Database design](docs/DATABASE_DESIGN.md)
- [Project presentation](docs/HabitFlow_Presentation.pptx)
