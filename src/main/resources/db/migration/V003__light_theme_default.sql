-- Existing Dark values may be either an explicit choice or the old default, so preserve
-- every stored theme value. The flag records choices made after this migration.
ALTER TABLE user_preferences ADD COLUMN IF NOT EXISTS theme_selected BOOLEAN NOT NULL DEFAULT FALSE;

-- New accounts with no stored preference use Light Mode.
ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'light';
