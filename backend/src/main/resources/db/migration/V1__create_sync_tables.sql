CREATE TABLE tasks (
    id VARCHAR(128) PRIMARY KEY,
    title TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('TODO', 'DOING', 'DONE')),
    version BIGINT NOT NULL CHECK (version > 0),
    deleted_at_millis BIGINT NULL
);

CREATE TABLE sync_operations (
    operation_id VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(256) NOT NULL,
    response_status VARCHAR(32) NULL,
    server_version BIGINT NULL,
    tombstone_version BIGINT NULL,
    deleted_at_millis BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NULL
);

CREATE INDEX sync_operations_created_at_idx ON sync_operations (created_at);
