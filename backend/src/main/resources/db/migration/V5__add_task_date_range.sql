ALTER TABLE tasks
    ADD COLUMN start_date_millis BIGINT NULL;

ALTER TABLE tasks
    ADD COLUMN due_date_millis BIGINT NULL;
