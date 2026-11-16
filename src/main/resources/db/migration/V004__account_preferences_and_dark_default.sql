-- Add account settings while preserving all stored theme selections.
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(80);
UPDATE users SET full_name = display_name WHERE full_name IS NULL;
ALTER TABLE users ALTER COLUMN full_name SET DEFAULT '';
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;

ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS workspace_name VARCHAR(80) NOT NULL DEFAULT 'Personal workspace';
ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS daily_reminder_time TIME;
ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS weekly_goal_days INTEGER NOT NULL DEFAULT 5;
ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'dark';
