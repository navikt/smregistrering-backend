package no.nav.syfo.persistering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.util.toPGObject
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun DatabaseInterface.opprettManuellOppgave(papirSmRegistering: PapirSmRegistering, oppgaveId: Int?, ferdigstilt: Boolean = false) {
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
            it.setTimestamp(6, Timestamp.from(papirSmRegistering.datoOpprettet?.toInstant()))
            it.setObject(7, oppgaveId)
            it.setBoolean(8, ferdigstilt)
            it.setObject(9, toPGObject(papirSmRegistering)) // Store it all so frontend can present whatever is present
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

fun DatabaseInterface.ferdigstillSmRegistering(
    sykmeldingId: String,
    utfall: String,
    ferdigstiltAv: String,
    avvisningsgrunn: String? = null
): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?,
                utfall = ?,
                ferdigstilt_av = ?,
                dato_ferdigstilt = ?,
                avvisningsgrunn = ?
            WHERE id = ?;
            """
        ).use {
            it.setBoolean(1, true)
            it.setString(2, utfall)
            it.setString(3, ferdigstiltAv)
            it.setTimestamp(4, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
            it.setString(5, avvisningsgrunn)
            it.setString(6, sykmeldingId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }

fun DatabaseInterface.slettSykmelding(sykmeldingId: String): Int =
    connection.use { connection ->
        val statusSendtSykmeldingHistory = connection.prepareStatement(
            """
            DELETE FROM sendt_sykmelding_history
            WHERE sykmelding_id = ?;
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }
        val statusManuellOppgave = connection.prepareStatement(
            """
            DELETE FROM MANUELLOPPGAVE
            WHERE id = ?;
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }
        val statusJob = connection.prepareStatement(
            """
            DELETE FROM job
            WHERE sykmelding_id = ?;
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }
        val statusSendtSykmelding = connection.prepareStatement(
            """
            DELETE FROM sendt_sykmelding
            WHERE sykmelding_id = ?;
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }
        connection.commit()
        return statusSendtSykmelding + statusSendtSykmeldingHistory + statusJob + statusManuellOppgave
    }
