CREATE TABLE IF NOT EXISTS habit_preferences (
    user_id BIGINT NOT NULL,
    habit_id BIGINT NOT NULL,
    time_of_day VARCHAR(20) NOT NULL DEFAULT 'Anytime',
    PRIMARY KEY (user_id, habit_id),
    CONSTRAINT fk_habit_prefs_owner FOREIGN KEY (habit_id, user_id) REFERENCES habits(id, user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS habit_daily_targets (
    user_id BIGINT NOT NULL,
    habit_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    target_count INTEGER NOT NULL DEFAULT 0,
    target_total INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, habit_id, target_date),
    CONSTRAINT fk_daily_target_owner FOREIGN KEY (habit_id, user_id) REFERENCES habits(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_target_count CHECK (target_total BETWEEN 1 AND 50 AND target_count BETWEEN 0 AND target_total)
);

CREATE INDEX IF NOT EXISTS ix_notes_user_date ON habit_notes(user_id, note_date);
CREATE INDEX IF NOT EXISTS ix_targets_user_date ON habit_daily_targets(user_id, target_date);
