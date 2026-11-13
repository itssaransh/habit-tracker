# Database Design

The app uses H2 during local development and PostgreSQL when deployed. `src/main/resources/schema.sql` creates the original base tables. Versioned SQL files under `src/main/resources/db/migration/` add account and workspace tables, and `schema_migrations` records which changes have run. The recorded PostgreSQL integration run used an isolated local PostgreSQL 18.6 database; production Neon has not been inspected or migrated for this release.

`V001__user_accounts.sql` creates users and profile preferences, adds nullable ownership to existing habits and check-ins, and adds notes and focus sessions. Check-ins are assigned only when their existing habit already has an owner. `V002__workspace_sync.sql` adds per-habit time preferences and daily targets with ownership constraints. Neither migration drops or truncates tables. Existing shared rows with no reliable owner remain unassigned and hidden; the application does not assign them to the first account that registers.

## Tables and relationship

Each account owns its habits, preferences, notes, targets, check-ins, and focus sessions. A habit can have several check-ins, notes, daily targets, and focus sessions. The authenticated account ID comes from the server session; API requests do not provide an owner ID.

```text
users (one) ----- (many) habits (one) ----- (many) habit_checkins
  id                  user_id, id            habit_id, user_id
       |                       |
       +-- user_preferences    +-- habit_notes
       +-- focus_sessions      +-- habit_preferences
                               +-- habit_daily_targets
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
| `user_id` | BIGINT, nullable for old shared records | Owning account for new habits |

## habit_checkins table

| Column | Type | Use |
| --- | --- | --- |
| `id` | BIGINT identity | Primary key for a check-in |
| `habit_id` | BIGINT | Links to `habits.id` |
| `checkin_date` | DATE | Date the habit was completed |
| `note` | VARCHAR(250) | Optional check-in note |
| `user_id` | BIGINT, nullable only for old shared records | Owning account for new check-ins |

The unique key on (`habit_id`, `checkin_date`) allows one check-in for each habit on a date. The foreign key uses `ON DELETE CASCADE`, so deleting a habit also deletes its check-ins.

## Account and workspace tables

- `users` stores a normalized unique email, display name, and BCrypt password hash. Raw passwords are not stored.
- `user_preferences` stores each account's theme.
- `habit_notes` stores dated reflections and links each row to both the owner and their habit.
- `habit_preferences` stores a habit's time-of-day preference.
- `habit_daily_targets` stores the progress and total for a habit on a particular date.
- `focus_sessions` stores the owner, optional habit, start time, and minutes.

Compound ownership foreign keys connect habit records to the same account. DAO queries also filter by the session's user ID. Earlier shared rows are left in place with no owner and are excluded from every account view; they are not assigned to the first person who signs up.

## JDBC operations

`JdbcHabitDao` and `JdbcWorkspaceDao` use prepared statements and require the owner ID on reads and writes. Connections, statements, and results use try-with-resources. When the dashboard loads, the DAO groups dates in a map and stores each habit's dates in a set. `HabitMetrics` uses those dates to calculate progress.

H2 uses its `MERGE` statement for check-ins. PostgreSQL uses `INSERT ... ON CONFLICT` with the same unique key. This lets the user mark a habit complete more than once without creating duplicate rows.

Login and signup establish an HTTP session that contains the server-authenticated account. Login rotates the session ID. Forms and write APIs require a session CSRF token. Workspace changes are sent to the Java API and persisted in the configured database; browser storage does not define identity. Cross-device synchronization is intended by this design but has not been verified on physical devices.

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

## Production migration and recovery precautions

The app checks `schema_migrations` and applies missing migrations during startup. PostgreSQL migration execution is transactional and records a version after its SQL completes. This protects the normal startup path from a partially applied migration, but the SQL assumes the expected baseline tables and has not been checked against the existing Neon schema. Do not run these migrations against production until a verified Neon backup or recovery point exists and the migration has been reviewed on a safe copy of that schema. Do not manually rerun an unrecorded migration until checking the actual schema and `schema_migrations` state.

The migrations contain no `DROP TABLE`, `TRUNCATE`, or bulk ownership assignment. Legacy unowned records are kept but hidden. There is no automatic legacy-owner bootstrap. If a deployment fails after a migration starts, preserve the database and logs, inspect the migration version and schema, then prepare a forward fix. Do not roll the database back by deleting tables. A code rollback to the old shared-workspace application is unsafe after private account records have been written because that old code did not apply user ownership filters. If recovery from a Neon point-in-time restore is needed, first assess the restore point and any user writes made after it; do not restore over newer data without a reviewed recovery plan.

The recorded isolated PostgreSQL 18.6 integration run passed 3 tests, alongside 9 unit tests. This does not establish compatibility with the production Neon schema or verify a production migration.

## Team members

- **Shaswat Gupta:** OOP models and schedule classes.
- **Saransh Mishra:** Database schema, JDBC configuration, and DAO classes.
- **Ayush Sharma:** Service validation, exception handling, collections, and tests.
- **Shivansh Singh:** Servlets and web UI.
