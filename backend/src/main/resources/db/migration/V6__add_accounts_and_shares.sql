CREATE TABLE app_users (
    id VARCHAR(128) PRIMARY KEY,
    contact VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at_millis BIGINT NOT NULL
);

CREATE TABLE verification_codes (
    id VARCHAR(128) PRIMARY KEY,
    contact VARCHAR(320) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    created_at_millis BIGINT NOT NULL,
    expires_at_millis BIGINT NOT NULL,
    used_at_millis BIGINT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX verification_codes_lookup_idx
    ON verification_codes (contact, purpose, created_at_millis DESC);

CREATE TABLE auth_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    created_at_millis BIGINT NOT NULL,
    expires_at_millis BIGINT NOT NULL
);

CREATE INDEX auth_sessions_user_idx ON auth_sessions (user_id);

CREATE TABLE task_share_packages (
    share_code VARCHAR(32) PRIMARY KEY,
    owner_user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    payload_json TEXT NOT NULL,
    created_at_millis BIGINT NOT NULL,
    expires_at_millis BIGINT NOT NULL
);

CREATE INDEX task_share_packages_expiry_idx ON task_share_packages (expires_at_millis);
