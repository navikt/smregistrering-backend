package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Route.hentPapirSykmeldingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService
) {
    route("/api/v1") {
        get("/hentPapirSykmeldingManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentPapirSykmeldingManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            when {
                oppgaveId == null -> {
                    log.info("Mangler query parameters: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty() -> {
                    log.info(
                        "Fant ingen uløste manuelloppgaver med oppgaveid {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                    )
                    call.respond(
                        HttpStatusCode.NoContent,
                        "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId"
                    )
                }
                else -> {
                    log.info(
                        "Henter ut oppgave med {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                    )
                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)

                    call.respond(manuellOppgaveDTOList)
                }
            }
        }
    }
}
