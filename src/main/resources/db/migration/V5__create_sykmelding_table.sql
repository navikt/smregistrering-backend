CREATE TABLE sendt_sykmelding (
    sykmelding_id VARCHAR primary key,
    sykmelding JSONB not null,
    timestamp TIMESTAMP with time zone not null
)
