# Personal Habit Tracker

> **[Open the live demo](https://personal-habit-tracker-3vv1.onrender.com/)**

**Live website:** [https://personal-habit-tracker-3vv1.onrender.com/](https://personal-habit-tracker-3vv1.onrender.com/)

Personal Habit Tracker is a Java web app for creating habits and recording daily check-ins. The dashboard shows streaks and recent progress. The page also adjusts for smaller screens. The name displayed in the app is HabitFlow.

This is a Java subject project built with Servlets, JSP, JDBC, and Maven.

## Features

- Add a habit with a category, schedule, description, and accent color.
- Choose a daily, weekday, or weekly schedule.
- Record or undo today's check-in.
- See current and best streaks, weekly check-ins, and completion rate.
- Delete habits and their check-in history.
- Use a local H2 database or a hosted PostgreSQL database.

## Technology

- Java 17
- Maven
- Jakarta Servlet 6.0 and JSP 3.1
- Apache Tomcat 10.1
- JDBC
- H2 for local runs; PostgreSQL for deployment
- HTML, CSS, JavaScript, and JSTL

## Run locally

Install JDK 17 and Maven 3.9 or newer. Build the app and run its tests:

```sh
mvn clean verify
```

This creates `target/habitflow.war`. Copy it into the `webapps` folder of Tomcat 10.1 and start Tomcat. Open `http://localhost:8080/habitflow/`.

By default, the app creates an H2 database under Tomcat's `data/habitflow` folder. The database file stays on disk between restarts. You can choose another folder with `APP_DATA_DIR`. For example, set it to `D:/habit-tracker-data` on Windows or `/var/lib/habit-tracker` on Linux before starting Tomcat.

For a local Docker image, run:

```sh
docker build -t personal-habit-tracker .
docker run --rm -p 10000:10000 -e PORT=10000 -e APP_DATA_DIR=/data -v habit-tracker-data:/data personal-habit-tracker
```

Open `http://localhost:10000/` after the container starts.

## Database configuration

Local development uses H2 and does not need a database account. The schema is in `src/main/resources/schema.sql` and is applied when the web app starts.

Production mode requires PostgreSQL. Set these environment variables on the hosting service:

| Variable | Value |
| --- | --- |
| `APP_ENV` | `production` |
| `JDBC_URL` | `jdbc:postgresql://HOST/DATABASE?sslmode=require&prepareThreshold=0` |
| `JDBC_USER` | PostgreSQL role name |
| `JDBC_PASSWORD` | PostgreSQL role password |

The database password belongs in the hosting provider's secret settings, not in this repository. `prepareThreshold=0` is set when using a hosted connection pooler.

## Deploy on Render and Neon

The repository includes a Dockerfile and `render.yaml` for a Render Docker web service. The container builds a Java 17 WAR and runs it on Tomcat 10.1. Render uses the `/health` endpoint to check that the app is ready.
The hosted app uses the India time zone for its daily check-ins.

To deploy it:

1. Create a PostgreSQL project in [Neon](https://neon.com/). In the Connect panel, select the database and copy the pooled connection details. The current free plan lists 1 GB of database storage and 100 CU-hours per month.
2. In Render, create a Blueprint from this public GitHub repository and choose the free web service plan.
3. When Render asks for the secret values from `render.yaml`, enter `JDBC_URL`, `JDBC_USER`, and `JDBC_PASSWORD`. Build the JDBC URL from the Neon host and database name using the format above. Enter the database role and password separately. Do not include those credentials in the URL or repository.
4. Wait for Render's build and health check to pass. Copy the URL shown on the service dashboard and add it to the top of this README only after opening it and confirming that the app loads.

Render's free web service sleeps after 15 minutes without a request and can take about a minute to wake up. Its local disk is temporary, so the app uses Neon for records that should stay after redeploys and restarts. Free hosting is suitable for a class demo and does not provide a production uptime guarantee. See [Render free plan details](https://render.com/docs/free), [Neon free plan details](https://neon.com/blog/neon-free-plan-1-gb-per-project), and [Render Blueprints](https://render.com/docs/blueprint-spec).

After Render is connected to the repository, pushes to `main` can trigger a new deploy. If automatic deploys are turned off, choose **Manual Deploy > Deploy latest commit** in the Render dashboard. The Neon database settings stay in Render's environment settings, and the database records remain outside the app container.

## Public demo note

This version does not have user accounts. A public deployment shares one habit list with everyone who visits it, and visitors can add or delete habits. Do not store private information in a public demo.

## Project folders

```text
src/main/java/com/habitflow/
  config/       Database connection and startup listener
  dao/          Database interface and JDBC implementation
  model/        Habit data, schedules, and progress calculations
  service/      Input validation and habit operations
  servlet/      Dashboard and form request handlers
src/main/resources/schema.sql
src/main/webapp/              JSP page, CSS, and JavaScript
src/test/java/                Service and JDBC tests
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
