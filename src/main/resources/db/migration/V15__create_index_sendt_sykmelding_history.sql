CREATE INDEX sykmelding_id_history_index
    ON sendt_sykmelding_history (sykmelding_id);

CREATE INDEX sykmelding_id_sent_index
    ON sendt_sykmelding (sykmelding_id);