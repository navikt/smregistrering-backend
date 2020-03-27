CREATE TABLE manuelloppgave (
  id CHAR(64) PRIMARY KEY,
  journalpost_id CHAR(64) NOT NULL,
  fnr CHAR(11) NULL,
  aktor_id CHAR(64) NULL,
  dokument_info_id CHAR(64) NULL,
  dato_opprettet TIMESTAMP NULL,
  oppgave_id INT,
  ferdigstilt boolean NOT NULL
);