ALTER TABLE users
    ADD deleted_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE tokens
    ADD last_used_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE tokens
    ADD revoked_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX idx_tokens_expires_at ON tokens (expires_at);

CREATE INDEX idx_tokens_user_active ON tokens (user_id, revoked_at, expires_at);

CREATE INDEX idx_users_deleted_at ON users (deleted_at);

CREATE INDEX idx_users_username ON users (username);

ALTER TABLE tokens
    DROP COLUMN is_revoked;

ALTER TABLE users
    ALTER COLUMN avatar_url TYPE VARCHAR(255) USING (avatar_url::VARCHAR(255));

ALTER TABLE tokens
    ALTER COLUMN device_info TYPE VARCHAR(255) USING (device_info::VARCHAR(255));

ALTER TABLE tokens
    ALTER COLUMN ip_address TYPE VARCHAR(255) USING (ip_address::VARCHAR(255));

ALTER TABLE roles
    ALTER COLUMN name TYPE VARCHAR(255) USING (name::VARCHAR(255));

ALTER TABLE tokens
    ALTER COLUMN token_hash TYPE VARCHAR(255) USING (token_hash::VARCHAR(255));

UPDATE roles SET name = 'ROLE_USER' WHERE name = 'USER';
UPDATE roles SET name = 'ROLE_ADMIN' WHERE name = 'ADMIN';

SELECT setval(pg_get_serial_sequence('roles', 'id'), coalesce(max(id), 0) + 1, false) FROM roles;