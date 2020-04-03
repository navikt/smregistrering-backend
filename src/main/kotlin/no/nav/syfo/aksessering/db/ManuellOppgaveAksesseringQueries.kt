package no.nav.syfo.aksessering.db

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.PapirManuellOppgave

fun DatabaseInterface.hentManuellOppgaver(oppgaveId: Int): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, journalpost_id, fnr, aktor_id, dokument_info_id, dato_opprettet, oppgave_id, ferdigstilt
                FROM MANUELLOPPGAVE  
                WHERE oppgave_id=? 
                AND ferdigstilt=?;
                """
        ).use {
            it.setInt(1, oppgaveId)
            it.setBoolean(2, false)
            it.executeQuery().toList { toManuellOppgaveDTO() }
        }
    }

fun DatabaseInterface.hentPapirManuellOppgave(oppgaveId: Int): List<PapirManuellOppgave> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, fnr, oppgave_id
                FROM MANUELLOPPGAVE  
                WHERE oppgave_id=? 
                AND ferdigstilt=?;
                """
        ).use {
            it.setInt(1, oppgaveId)
            it.setBoolean(2, false)
            it.executeQuery().toList { toPapirManuellOppgave() }
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO =
    ManuellOppgaveDTO(
        journalpostId = getString("journalpost_id")?.trim() ?: "",
        fnr = getString("fnr")?.trim(),
        aktorId = getString("aktor_id")?.trim(),
        dokumentInfoId = getString("dokument_info_id")?.trim(),
        datoOpprettet = getTimestamp("dato_opprettet").toLocalDateTime(),
        sykmeldingId = getString("id")?.trim() ?: "",
        oppgaveid = getInt("oppgave_id"),
        ferdigstilt = getBoolean("ferdigstilt"),
        pdfPapirSykmelding = null
    )

fun ResultSet.toPapirManuellOppgave(): PapirManuellOppgave =
    PapirManuellOppgave(
        fnr = getString("fnr")?.trim(),
        sykmeldingId = getString("id")?.trim() ?: "",
        oppgaveid = getInt("oppgave_id"),
        pdfPapirSykmelding = null
    )
