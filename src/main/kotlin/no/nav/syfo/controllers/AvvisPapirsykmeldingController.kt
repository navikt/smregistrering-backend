package no.nav.syfo.controllers

import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillRegistrering
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.Utfall
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.logNAVEpostFromTokenToSecureLogs
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class AvvisPapirsykmeldingController(
    private val authorizationService: AuthorizationService,
    private val sykmelderService: SykmelderService,
    private val manuellOppgaveDAO: ManuellOppgaveDAO,
    private val oppgaveService: OppgaveService,
    private val journalpostService: JournalpostService,
) {

    suspend fun avvisPapirsykmelding(
        oppgaveId: Int,
        accessToken: String,
        navEnhet: String,
        avvisSykmeldingReason: String?,
    ): HttpServiceResponse {

        val callId = UUID.randomUUID().toString()

        val manuellOppgaveDTOList = manuellOppgaveDAO.hentManuellOppgaver(oppgaveId)
        if (manuellOppgaveDTOList.isEmpty()) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return HttpServiceResponse(HttpStatusCode.NoContent)
        } else {
            val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
            val journalpostId = manuellOppgaveDTOList.first().journalpostId
            val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId
            val pasientFnr = manuellOppgaveDTOList.first().fnr!!

            val loggingMeta = LoggingMeta(
                mottakId = sykmeldingId,
                dokumentInfoId = dokumentInfoId,
                msgId = callId,
                sykmeldingId = sykmeldingId,
                journalpostId = journalpostId
            )

            /***
             * Vi antar at pasientFnr finnes da sykmeldinger må ha fnr fylt ut
             * for å bli routet til smregistrering-backend (håndtert av syfosmpapirmottak)
             */
            if (authorizationService.hasAccess(accessToken, pasientFnr)) {
                logNAVEpostFromTokenToSecureLogs(accessToken, true)
                val veileder = authorizationService.getVeileder(accessToken)

                val hpr = manuellOppgaveDTOList.first().papirSmRegistering?.behandler?.hpr
                val sykmelder = finnSykmelder(hpr, callId, oppgaveId)

                val hentOppgave = oppgaveService.hentOppgave(oppgaveId, sykmeldingId)
                val ferdigstillRegistrering = FerdigstillRegistrering(
                    oppgaveId = oppgaveId,
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasientFnr = pasientFnr,
                    sykmeldingId = sykmeldingId,
                    sykmelder = sykmelder,
                    navEnhet = navEnhet,
                    veileder = veileder,
                    avvist = true,
                    oppgave = hentOppgave
                )

                journalpostService.ferdigstillJournalpost(accessToken, ferdigstillRegistrering, loggingMeta)
                oppgaveService.ferdigstillOppgave(
                    oppgaveId = oppgaveId,
                    ferdigstillRegistrering = ferdigstillRegistrering,
                    beskrivelse = lagOppgavebeskrivelse(
                        avvisSykmeldingReason,
                        hentOppgave.beskrivelse,
                        veileder,
                        navEnhet
                    ),
                    loggingMeta = loggingMeta
                )

                manuellOppgaveDAO.ferdigstillSmRegistering(
                    sykmeldingId = sykmeldingId,
                    utfall = Utfall.AVVIST,
                    ferdigstiltAv = veileder.veilederIdent,
                    avvisningsgrunn = avvisSykmeldingReason
                ).also {
                    if (it < 1) {
                        log.warn(
                            "Ferdigstilling av papirsm i database rapporterer update count < 1 for oppgave {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
                    }
                }

                return HttpServiceResponse(HttpStatusCode.NoContent)
            } else {
                log.warn("Veileder har ikkje tilgang, {}", StructuredArguments.keyValue("oppgaveId", oppgaveId))
                logNAVEpostFromTokenToSecureLogs(accessToken, false)
                return HttpServiceResponse(HttpStatusCode.Forbidden)
            }
        }
    }

    fun lagOppgavebeskrivelse(
        avvisSykmeldingReason: String?,
        opprinneligBeskrivelse: String?,
        veileder: Veileder,
        navEnhet: String,
        timestamp: LocalDateTime? = null,
    ): String {
        val oppdatertBeskrivelse = when {
            !avvisSykmeldingReason.isNullOrEmpty() -> "Avvist papirsykmelding med årsak: $avvisSykmeldingReason"
            else -> "Avvist papirsykmelding uten oppgitt årsak."
        }
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val formattedTimestamp = (timestamp ?: LocalDateTime.now()).format(formatter)
        return "--- $formattedTimestamp ${veileder.veilederIdent}, $navEnhet ---\n$oppdatertBeskrivelse\n\n$opprinneligBeskrivelse"
    }

    private suspend fun finnSykmelder(
        hpr: String?,
        callId: String,
        oppgaveId: Int,
    ): Sykmelder {
        return if (!hpr.isNullOrEmpty()) {
            log.info("Henter sykmelder fra HPR og PDL for oppgaveid $oppgaveId")
            try {
                sykmelderService.hentSykmelder(hpr, callId)
            } catch (e: Exception) {
                log.warn("Noe gikk galt ved henting av sykmelder fra HPR eller PDL for oppgaveid $oppgaveId")
                return getDefaultSykmelder()
            }
        } else {
            getDefaultSykmelder()
        }
    }

    private fun getDefaultSykmelder(): Sykmelder =
        Sykmelder(
            fornavn = "Helseforetak",
            mellomnavn = null,
            etternavn = "",
            aktorId = null,
            hprNummer = null,
            fnr = null,
            godkjenninger = null
        )
}
