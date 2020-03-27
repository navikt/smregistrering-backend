package no.nav.syfo.persistering.db

import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.PapirSmRegistering

fun DatabaseInterface.opprettManuellOppgave(papirSmRegistering: PapirSmRegistering, oppgaveId: Int, pdfPapirsykmelding: ByteArray?) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO manuelloppgave(
                id,
                journalpost_id,
                fnr,
                aktor_id,
                dokument_info_id,
                dato_opprettet,
                oppgave_id,
                ferdigstilt,
                pdf_papir_sykmelding
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, papirSmRegistering.sykmeldingId)
            it.setString(2, papirSmRegistering.journalpostId)
            it.setString(3, papirSmRegistering.fnr)
            it.setString(4, papirSmRegistering.aktorId)
            it.setString(5, papirSmRegistering.dokumentInfoId)
            it.setTimestamp(6, Timestamp.valueOf(papirSmRegistering.datoOpprettet))
            it.setInt(7, oppgaveId)
            it.setBoolean(8, false)
            it.setBytes(9, pdfPapirsykmelding)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.erOpprettManuellOppgave(sykmledingsId: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """
        ).use {
            it.setString(1, sykmledingsId)
            it.executeQuery().next()
        }
    }
