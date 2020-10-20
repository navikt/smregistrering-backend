package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.sendOppgaveTilGosys(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/tilgosys") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/tilgosys")

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

                    if (authorizationService.hasAccess(accessToken, pasientFnr)) {

                        val veileder = authorizationService.getVeileder(accessToken) // Trenger kanskje

                        log.info("Sender oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))

                        oppgaveClient.setOppgaveTilGosysOppgave(
                            oppgaveId = oppgaveId,
                            msgId = callId,
                            tildeltEnhetsnr = navEnhet,
                            tilordnetRessurs = veileder.veilederIdent
                        )
                        manuellOppgaveService.ferdigstillSmRegistering(oppgaveId)

                        log.info("Ferdig å sende oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))
                    } else {
                        log.warn("Veileder har ikke tilgang, {}", StructuredArguments.keyValue("oppgaveId", oppgaveId))
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
    }
}
