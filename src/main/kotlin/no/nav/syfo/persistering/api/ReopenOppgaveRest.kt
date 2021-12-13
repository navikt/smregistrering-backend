package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.registerReopenOppgaveApi(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/reopen") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/reopen")

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

                    // TODO: Skriv tester

                    val hentManuellOppgaver = manuellOppgaveService.hentManuellOppgaver(oppgaveId, true)

                    if (!hentManuellOppgaver.isNullOrEmpty()) {
                        val manuellOppgave = hentManuellOppgaver.first()
                        if (authorizationService.hasAccess(accessToken, manuellOppgave.fnr!!)) {
                            val updated = manuellOppgaveService.gjenaapneManuellOppgave(oppgaveId)
                            if (updated > 0) {
                                call.respond(HttpStatusCode.OK, "Gjenåpnet oppgave for oppgaveId $oppgaveId")
                            } else {
                                call.respond(HttpStatusCode.NotModified, "Ingen endringer utført for oppgaveId $oppgaveId")
                            }
                        } else {
                            log.warn(
                                "Veileder har ikke tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            call.respond(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til oppgaven")
                        }
                    } else {
                        log.warn("Fant ingen lukkede oppgaver for oppgaveId $oppgaveId")
                        call.respond(HttpStatusCode.NotFound, "Fant ingen lukkede oppgaver å gjenåpne")
                    }
                }
            }
        }
    }
}
