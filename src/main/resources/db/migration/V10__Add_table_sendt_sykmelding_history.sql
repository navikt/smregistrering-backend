create table sendt_sykmelding_history
(
    id CHAR(64) PRIMARY KEY,
    sykmelding_id CHAR(64) REFERENCES manuelloppgave(id),
    ferdigstilt_av VARCHAR,
    dato_ferdigstilt TIMESTAMP,
    sykmelding jsonb
);

