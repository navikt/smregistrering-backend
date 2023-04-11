package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.helpers.log
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.FerdigstillRegistrering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.util.LoggingMeta
import java.time.DayOfWeek
import java.time.LocalDate

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
) {

    suspend fun hentOppgave(oppgaveId: Int, sykmeldingId: String): Oppgave {
        return oppgaveClient.hentOppgave(oppgaveId, sykmeldingId)
    }

    suspend fun upsertOppgave(
        papirSmRegistering: PapirSmRegistering,
        loggingMeta: LoggingMeta,
    ): Oppgave {
        return if (papirSmRegistering.oppgaveId == null || (papirSmRegistering.oppgaveId.toIntOrNull() == null)) {
            val opprettOppgave = OpprettOppgave(
                aktoerId = papirSmRegistering.aktorId,
                opprettetAvEnhetsnr = "9999",
                behandlesAvApplikasjon = "SMR",
                beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                tema = "SYM",
                oppgavetype = "JFR",
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                    LocalDate.now().plusDays(4),
                ),
                prioritet = "HOY",
                journalpostId = papirSmRegistering.journalpostId,
            )

            val opprettetOppgave = oppgaveClient.opprettOppgave(opprettOppgave, papirSmRegistering.sykmeldingId)
            OPPRETT_OPPGAVE_COUNTER.inc()
            log.info(
                "Opprettet manuell papirsykmeldingoppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", opprettetOppgave.id),
                StructuredArguments.fields(loggingMeta),
            )
            opprettetOppgave
        } else {
            val oppdatertOppgave = patchManuellOppgave(
                papirSmRegistering.oppgaveId.toInt(),
                loggingMeta.msgId,
            )
            log.info(
                "Patchet manuell papirsykmeldingsoppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppdatertOppgave.id),
                StructuredArguments.fields(loggingMeta),
            )
            oppdatertOppgave
        }
    }

    suspend fun ferdigstillOppgave(
        ferdigstillRegistrering: FerdigstillRegistrering,
        beskrivelse: String?,
        loggingMeta: LoggingMeta,
        oppgaveId: Int,
    ) {
        val oppgave = when {
            ferdigstillRegistrering.oppgave != null -> {
                ferdigstillRegistrering.oppgave
            }
            else -> {
                oppgaveClient.hentOppgave(oppgaveId, ferdigstillRegistrering.sykmeldingId)
            }
        }

        if (OppgaveStatus.FERDIGSTILT.name != oppgave.status) {
            val ferdigstillOppgave = FerdigstillOppgave(
                versjon = oppgave.versjon
                    ?: throw RuntimeException("Fant ikke versjon for oppgave ${oppgave.id}, sykmeldingId ${ferdigstillRegistrering.sykmeldingId}"),
                id = oppgaveId,
                status = OppgaveStatus.FERDIGSTILT,
                tildeltEnhetsnr = ferdigstillRegistrering.navEnhet,
                tilordnetRessurs = ferdigstillRegistrering.veileder.veilederIdent,
                mappeId = null,
                beskrivelse = if (beskrivelse?.isNotBlank() == true) beskrivelse else oppgave.beskrivelse,
            )

            val ferdigStiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, ferdigstillRegistrering.sykmeldingId)
            log.info(
                "Ferdigstiller oppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", ferdigStiltOppgave.id),
                StructuredArguments.fields(loggingMeta),
            )
        } else {
            log.info("Hopper over ferdigstillOppgave, oppgaveId $oppgaveId er allerede ${oppgave.status}")
        }
    }

    suspend fun sendOppgaveTilGosys(oppgaveId: Int, msgId: String, tilordnetRessurs: String): Oppgave {
        val oppgave = oppgaveClient.hentOppgave(oppgaveId, msgId)
        val oppdatertOppgave = oppgave.copy(
            behandlesAvApplikasjon = "FS22",
            tilordnetRessurs = tilordnetRessurs,
            mappeId = null,
        )
        return oppgaveClient.oppdaterOppgave(oppdatertOppgave, msgId)
    }

    suspend fun patchManuellOppgave(oppgaveId: Int, msgId: String): Oppgave {
        val oppgave = oppgaveClient.hentOppgave(oppgaveId, msgId)
        if (oppgave.status == "FERDIGSTILT") {
            log.warn("Oppgave med id $oppgaveId er allerede ferdigstilt. Oppretter ny oppgave for msgId $msgId")
            return oppgaveClient.opprettOppgave(
                OpprettOppgave(
                    aktoerId = oppgave.aktoerId,
                    opprettetAvEnhetsnr = "9999",
                    behandlesAvApplikasjon = "SMR",
                    beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                    tema = "SYM",
                    oppgavetype = "JFR",
                    aktivDato = LocalDate.now(),
                    fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                        LocalDate.now().plusDays(4),
                    ),
                    prioritet = "HOY",
                    journalpostId = oppgave.journalpostId,
                ),
                msgId,
            )
        } else {
            val patch = oppgave.copy(
                behandlesAvApplikasjon = "SMR",
                beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                mappeId = null,
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                    LocalDate.now().plusDays(4),
                ),
                prioritet = "HOY",
            )
            return oppgaveClient.oppdaterOppgave(patch, msgId)
        }
    }

    private fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
        return setToWorkDay(ferdistilleDato)
    }

    private fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
        when (ferdistilleDato.dayOfWeek) {
            DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
            DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
            else -> ferdistilleDato
        }
}
