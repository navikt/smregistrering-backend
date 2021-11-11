package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.AvvisSykmeldingRequest
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.Utfall
import no.nav.syfo.persistering.handleAvvisOppgave
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.avvisOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
    safJournalpostService: SafJournalpostService,
    sykmelderService: SykmelderService,
    dokArkivClient: DokArkivClient,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/avvis") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/avvis")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val avvisSykmeldingRequest: AvvisSykmeldingRequest? = call.receiveOrNull()

            when {
                oppgaveId == null -> {
                    log.error("Path parameter mangler eller er feil formattert: oppgaveid")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path parameter mangler eller er feil formattert: oppgaveid"
                    )
                }
                accessToken == null -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized, "Mangler JWT Bearer token i HTTP header")
                }
                navEnhet == null -> {
                    log.error("Mangler X-Nav-Enhet i http header")
                    call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i HTTP header")
                }
                else -> {

                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (manuellOppgaveDTOList.isEmpty()) {
                        log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                        call.respond(HttpStatusCode.NoContent)
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

                            val veileder = authorizationService.getVeileder(accessToken)

                            val hpr = manuellOppgaveDTOList.first().papirSmRegistering?.behandler?.hpr
                            val sykmelder = finnSykmelder(hpr, sykmelderService, callId, oppgaveId)

                            handleAvvisOppgave(
                                dokArkivClient = dokArkivClient,
                                oppgaveClient = oppgaveClient,
                                safJournalpostService = safJournalpostService,
                                sykmelder = sykmelder,
                                veileder = veileder,
                                journalpostId = journalpostId,
                                dokumentInfoId = dokumentInfoId,
                                loggingMeta = loggingMeta,
                                navEnhet = navEnhet,
                                oppgaveId = oppgaveId,
                                pasientFnr = pasientFnr,
                                sykmeldingId = sykmeldingId,
                                accessToken = accessToken,
                                avvisSykmeldingReason = avvisSykmeldingRequest?.reason
                            )
                            manuellOppgaveService.ferdigstillSmRegistering(
                                oppgaveId = oppgaveId,
                                utfall = Utfall.AVVIST,
                                ferdigstiltAv = veileder.veilederIdent
                            ).also {
                                if (it < 1) {
                                    log.warn(
                                        "Ferdigstilling av papirsm i database rapporterer update count < 1 for oppgave {}",
                                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                    )
                                }
                            }

                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            log.warn("Veileder har ikkje tilgang, {}", StructuredArguments.keyValue("oppgaveId", oppgaveId))
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun finnSykmelder(
    hpr: String?,
    sykmelderService: SykmelderService,
    callId: String,
    oppgaveId: Int
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
