package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.objectMapper

fun DatabaseInterface.hentManuellOppgaver(oppgaveId: Int): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, journalpost_id, fnr, aktor_id, dokument_info_id, dato_opprettet, oppgave_id, ferdigstilt, papir_sm_registrering
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
        papirSmRegistering = objectMapper.readValue(getString("papir_sm_registrering")),
        pdfPapirSykmelding = null
    )

fun ResultSet.toPapirManuellOppgave(): PapirManuellOppgave =
    PapirManuellOppgave(
        fnr = getString("fnr")?.trim(),
        sykmeldingId = getString("id")?.trim() ?: "",
        oppgaveid = getInt("oppgave_id"),
        pdfPapirSykmelding = ByteArray(1)
    )
