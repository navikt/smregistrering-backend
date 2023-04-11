package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.ZoneOffset

fun DatabaseInterface.hentManuellOppgaver(oppgaveId: Int, ferdigstilt: Boolean = false): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, journalpost_id, fnr, aktor_id, dokument_info_id, dato_opprettet, oppgave_id, ferdigstilt, papir_sm_registrering
                FROM MANUELLOPPGAVE  
                WHERE oppgave_id=? 
                AND ferdigstilt=?;
                """,
        ).use {
            it.setInt(1, oppgaveId)
            it.setBoolean(2, ferdigstilt)
            it.executeQuery().toList { toManuellOppgaveDTO() }
        }
    }

fun DatabaseInterface.hentManuellOppgaveForSykmelding(sykmeldingId: String): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, journalpost_id, fnr, aktor_id, dokument_info_id, dato_opprettet, oppgave_id, ferdigstilt, papir_sm_registrering
                FROM MANUELLOPPGAVE  
                WHERE id=? 
                """,
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toManuellOppgaveDTO() }
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO =
    ManuellOppgaveDTO(
        journalpostId = getString("journalpost_id")?.trim() ?: "",
        fnr = getString("fnr")?.trim(),
        aktorId = getString("aktor_id")?.trim(),
        dokumentInfoId = getString("dokument_info_id")?.trim(),
        datoOpprettet = getTimestamp("dato_opprettet").toInstant().atOffset(ZoneOffset.UTC),
        sykmeldingId = getString("id")?.trim() ?: "",
        oppgaveid = getInt("oppgave_id"),
        ferdigstilt = getBoolean("ferdigstilt"),
        papirSmRegistering = getString("papir_sm_registrering")?.let {
            objectMapper.readValue<PapirSmRegistering>(it)
        },
        pdfPapirSykmelding = null,
    )
