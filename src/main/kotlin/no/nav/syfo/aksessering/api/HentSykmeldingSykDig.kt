package no.nav.syfo.aksessering.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.OffsetDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.sykmelding.SendtSykmeldingService

fun Route.hentPapirSykmeldingManuellOppgaveTilSykDig(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    sendtSykmeldingService: SendtSykmeldingService,
) {
    route("/api/v1") {
        get("/oppgave/sykDig/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()
            log.info("Mottok kall til GET /api/v1/oppgave/${oppgaveId ?: "ukjent oppgaveId"}")

            if (oppgaveId == null) {
                return@get call.respond(HttpStatusCode.NotFound)
            }

            val manuellOppgaveDTOList =
                manuellOppgaveDAO.hentManuellOppgaverSykDig(oppgaveId, false) ?: emptyList()
            if (manuellOppgaveDTOList.isEmpty()) {
                log.info(
                    "Fant ingen manuelloppgaver med oppgaveid {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId),
                )
                return@get call.respond(
                    HttpStatusCode.NotFound,
                    "Fant ingen manuelle oppgaver med oppgaveid $oppgaveId",
                )
            }
            call.respond(manuellOppgaveDTOList)
        }

        get("/sykmelding/sykDig/{sykmeldingId}") {
            val sykmeldingId = call.parameters["sykmeldingId"]
            log.info(
                "Mottok kall til GET /api/v1/sykmelding/${sykmeldingId ?: "ukjent sykmeldingId"}"
            )
            if (sykmeldingId == null) {
                log.info("sykmeldingId er null")
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val sykmeldingHistory =
                sendtSykmeldingService.getReceivedSykmeldingHistory(sykmeldingId)
            if (sykmeldingHistory.isNotEmpty()) {
                return@get call.respond(sykmeldingHistory)
            }
            log.info("Ingen historikk funnet for sykmelding $sykmeldingId")

            val sykmelding =
                sendtSykmeldingService.getReceivedSykmelding(sykmeldingId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            val defaultHistory =
                listOf(
                    SendtSykmeldingHistory(
                        id = UUID.randomUUID().toString(),
                        sykmeldingId = sykmeldingId,
                        ferdigstiltAv = "",
                        datoFerdigstilt = OffsetDateTime.now(),
                        receivedSykmelding = sykmelding
                    )
                )
            call.respond(defaultHistory)
        }
    }
}
