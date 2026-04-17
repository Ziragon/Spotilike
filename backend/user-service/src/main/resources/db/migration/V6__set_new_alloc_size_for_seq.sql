ALTER TABLE tokens ALTER COLUMN id DROP IDENTITY IF EXISTS;

DROP SEQUENCE IF EXISTS tokens_id_seq CASCADE;

CREATE SEQUENCE tokens_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

SELECT setval('tokens_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM tokens), false);

ALTER TABLE tokens ALTER COLUMN id SET DEFAULT nextval('tokens_id_seq');
ALTER SEQUENCE tokens_id_seq OWNED BY tokens.id;

ALTER TABLE users ALTER COLUMN id DROP IDENTITY IF EXISTS;

DROP SEQUENCE IF EXISTS users_id_seq CASCADE;

CREATE SEQUENCE users_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM users), false);

ALTER TABLE tokens ALTER COLUMN id SET DEFAULT nextval('tokens_id_seq');
ALTER SEQUENCE tokens_id_seq OWNED BY tokens.id;