package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.persistering.handleAvvisOppgave
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.avvisOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
    sykmelderService: SykmelderService,
    dokArkivClient: DokArkivClient,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/avvis") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/sykmelder/$oppgaveId/avvis")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

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
                        val sykmelder = if (!hpr.isNullOrEmpty()) {
                            log.info("Henter sykmelder fra HPR og PDL")
                            sykmelderService.hentSykmelder(hpr, accessToken, callId)
                        } else {
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

                        if (manuellOppgaveService.ferdigstillSmRegistering(oppgaveId) > 0) {
                            handleAvvisOppgave(
                                dokArkivClient = dokArkivClient,
                                oppgaveClient = oppgaveClient,
                                sykmelder = sykmelder,
                                veileder = veileder,
                                journalpostId = journalpostId,
                                dokumentInfoId = dokumentInfoId,
                                loggingMeta = loggingMeta,
                                navEnhet = navEnhet,
                                oppgaveId = oppgaveId,
                                pasientFnr = pasientFnr,
                                sykmeldingId = sykmeldingId
                            )
                        }
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        log.warn("Veileder har ikkje tilgang, {}", StructuredArguments.keyValue("oppgaveId", oppgaveId))
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
    }
}
