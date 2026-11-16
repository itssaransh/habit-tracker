-- Light Mode is the default for new accounts and missing preferences.
-- Existing saved theme choices remain unchanged.
ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'light';
