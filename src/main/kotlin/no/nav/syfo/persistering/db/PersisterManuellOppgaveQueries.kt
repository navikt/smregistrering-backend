package no.nav.syfo.persistering.db

import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.toPGObject
import java.time.LocalDateTime
import java.time.ZoneId

fun DatabaseInterface.opprettManuellOppgave(papirSmRegistering: PapirSmRegistering, oppgaveId: Int) {
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
                papir_sm_registrering
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, papirSmRegistering.sykmeldingId)
            it.setString(2, papirSmRegistering.journalpostId)
            it.setString(3, papirSmRegistering.fnr)
            it.setString(4, papirSmRegistering.aktorId)
            it.setString(5, papirSmRegistering.dokumentInfoId)
            it.setTimestamp(6, Timestamp.valueOf(papirSmRegistering.datoOpprettet?.atZoneSameInstant(ZoneId.of("UTC"))?.toLocalDateTime()))
            it.setInt(7, oppgaveId)
            it.setBoolean(8, false)
            it.setObject(9, papirSmRegistering.toPGObject()) // Store it all so frontend can present whatever is present
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

fun DatabaseInterface.ferdigstillSmRegistering(oppgaveId: Int): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?
            WHERE oppgave_id = ?;
            """
        ).use {
            it.setBoolean(1, true)
            it.setInt(2, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }
