ALTER TABLE tokens
    ADD consumed_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE tokens
    ADD family_id UUID;

ALTER TABLE tokens
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_tokens_family_id ON tokens (family_id);