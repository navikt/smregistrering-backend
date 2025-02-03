package no.nav.syfo.aksessering.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.sykmelding.SendtSykmeldingService

fun Route.hentPapirSykmeldingManuellOppgaveTilSykDig(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    sendtSykmeldingService: SendtSykmeldingService,
) {
    route("/api/v1") {
        get("/oppgave/sykDig/{sykmeldingId}") {
            val sykmeldingId = call.parameters["sykmeldingId"]
            log.info("Mottok kall til GET /api/v1/oppgave/${sykmeldingId ?: "ukjent sykmeldingId"}")

            if (sykmeldingId == null) {
                return@get call.respond(HttpStatusCode.NotFound)
            }

            val manuellOppgaveDTOList =
                manuellOppgaveDAO.hentManuellOppgaverSykDig(sykmeldingId) ?: emptyList()
            if (manuellOppgaveDTOList.isEmpty()) {
                log.info(
                    "Fant ingen manuelloppgaver med oppgaveid {}",
                    StructuredArguments.keyValue("oppgaveId", sykmeldingId),
                )
                return@get call.respond(
                    HttpStatusCode.NotFound,
                    "Fant ingen manuelle oppgaver med oppgaveid $sykmeldingId",
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
            val sykmelding = sendtSykmeldingService.getReceivedSykmeldingWithTimestamp(sykmeldingId)

            if (sykmeldingHistory.isNotEmpty() && sykmelding != null) {
                val response =
                    sykmeldingHistory.map { sendSyk ->
                        SendtSykmeldingHistorySykDig(
                            id = sendSyk.id,
                            sykmeldingId = sendSyk.sykmeldingId,
                            ferdigstiltAv = sendSyk.ferdigstiltAv,
                            datoFerdigstilt = sendSyk.datoFerdigstilt.toLocalDateTime(),
                            timestamp = sykmelding.timestamp,
                            receivedSykmelding = sykmelding.receivedSykmelding
                        )
                    }
                return@get call.respond(response)
            }
            log.info("Ingen historikk funnet for sykmelding $sykmeldingId")
            if (sykmelding != null) {

                val defaultHistory =
                    listOf(
                        SendtSykmeldingHistorySykDig(
                            id = UUID.randomUUID().toString(),
                            sykmeldingId = sykmeldingId,
                            ferdigstiltAv = "",
                            datoFerdigstilt = sykmelding.timestamp.toLocalDateTime(),
                            receivedSykmelding = sykmelding.receivedSykmelding,
                            timestamp = sykmelding.timestamp
                        )
                    )
                return@get call.respond(defaultHistory)
            }
        }
    }
}

data class SendtSykmeldingHistorySykDig(
    val id: String,
    val sykmeldingId: String,
    val ferdigstiltAv: String,
    val datoFerdigstilt: LocalDateTime?,
    val timestamp: OffsetDateTime,
    val receivedSykmelding: ReceivedSykmelding,
)
