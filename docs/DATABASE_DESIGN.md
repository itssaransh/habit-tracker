# Database Design

The app uses H2 during local development and PostgreSQL when deployed. Both databases use `src/main/resources/schema.sql`. `Database.initialize()` runs the table definitions when the web app starts.

## Tables and relationship

One habit can have several check-ins. Each check-in belongs to one habit.

```text
habits (one) -------- (many) habit_checkins
  id                         habit_id
                             checkin_date
```

## habits table

| Column | Type | Use |
| --- | --- | --- |
| `id` | BIGINT identity | Primary key for a habit |
| `name` | VARCHAR(120) | Required habit name |
| `description` | VARCHAR(500) | Optional description |
| `category` | VARCHAR(40) | Habit category |
| `frequency` | VARCHAR(20) | Daily, Weekdays, or Weekly |
| `color` | VARCHAR(20) | Habit accent color |
| `created_at` | DATE | Date the habit was added |

## habit_checkins table

| Column | Type | Use |
| --- | --- | --- |
| `id` | BIGINT identity | Primary key for a check-in |
| `habit_id` | BIGINT | Links to `habits.id` |
| `checkin_date` | DATE | Date the habit was completed |
| `note` | VARCHAR(250) | Optional check-in note |

The unique key on (`habit_id`, `checkin_date`) allows one check-in for each habit on a date. The foreign key uses `ON DELETE CASCADE`, so deleting a habit also deletes its check-ins.

## JDBC operations

`JdbcHabitDao` uses prepared statements to read and update the tables. Connections, statements, and results use try-with-resources. When the dashboard loads, the DAO groups dates in a map and stores each habit's dates in a set. `HabitMetrics` uses those dates to calculate progress.

H2 uses its `MERGE` statement for check-ins. PostgreSQL uses `INSERT ... ON CONFLICT` with the same unique key. This lets the user mark a habit complete more than once without creating duplicate rows.

## Database settings

Local use defaults to a file-based H2 database in Tomcat's `data/habitflow` folder. Set `APP_DATA_DIR` to choose another local folder.

Production uses these environment variables:

| Variable | Description |
| --- | --- |
| `APP_ENV` | Set to `production` to disable the local database fallback |
| `JDBC_URL` | PostgreSQL JDBC URL with SSL enabled |
| `JDBC_USER` | PostgreSQL database role |
| `JDBC_PASSWORD` | PostgreSQL password |

Enter credentials in the hosting service's secret settings. Do not add them to source files or commit them to GitHub.

## Team members

- **Shaswat Gupta:** OOP models and schedule classes.
- **Saransh Mishra:** Database schema, JDBC configuration, and DAO classes.
- **Ayush Sharma:** Service validation, exception handling, collections, and tests.
- **Shivansh Singh:** Servlets and web UI.
