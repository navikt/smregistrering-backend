CREATE TABLE job (
    sykmelding_id VARCHAR references sendt_sykmelding(sykmelding_id),
    name VARCHAR,
    created TIMESTAMP with time zone not null,
    updated TIMESTAMP with time zone not null,
    status VARCHAR not null,
    PRIMARY KEY (sykmelding_id, name)
);

create index job_status_idx on job(status);
create index job_updated_idx on job(updated);
create index job_created_idx on job(created);
