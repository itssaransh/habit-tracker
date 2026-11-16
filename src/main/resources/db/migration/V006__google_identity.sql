-- Store Google's stable OIDC subject separately from the changeable email address.
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_sub ON users(google_sub);
