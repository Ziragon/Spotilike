DROP INDEX IF EXISTS idx_users_email;

ALTER TABLE users DROP CONSTRAINT IF EXISTS uc_users_email;

CREATE UNIQUE INDEX idx_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;