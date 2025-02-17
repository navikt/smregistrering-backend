CREATE INDEX idx_migrert_null_dato
    ON MANUELLOPPGAVE (dato_opprettet)
    WHERE migrert_timestamp IS NULL;

