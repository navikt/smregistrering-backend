ALTER TABLE manuelloppgave
    ADD COLUMN migrert_timestamp TIMESTAMP NULL DEFAULT NULL;

CREATE INDEX migrert_timestamp_index
    ON manuelloppgave (migrert_timestamp);
