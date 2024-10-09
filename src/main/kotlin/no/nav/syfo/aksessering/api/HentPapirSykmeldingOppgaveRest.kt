package no.nav.syfo.aksessering.api

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.model.Document
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.sikkerlogg
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentPapirSykmeldingManuellOppgave(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    safDokumentClient: SafDokumentClient,
    sendTilGosysController: SendTilGosysController,
    authorizationService: AuthorizationService,
) {
    route("/api/v1") {
        get("/oppgave/{oppgaveid}") {
            log.info("Mottok kall til GET /api/v1/oppgave/")
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til GET /api/v1/oppgave/$oppgaveId")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            log.info("access_token: $accessToken for oppgaveId $oppgaveId")
            val manuellOppgaveDTOList =
                oppgaveId?.let { manuellOppgaveDAO.hentManuellOppgaver(it) } ?: emptyList()
            log.info(
                "manuelloppgave dto for oppgaveid $oppgaveId : ${manuellOppgaveDTOList.first().oppgaveid}"
            )
            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized)
                }
                oppgaveId == null -> {
                    log.info("Ugyldig path parameter: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveDTOList.isEmpty() -> {
                    log.info(
                        "Fant ingen uløste manuelloppgaver med oppgaveid {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId),
                    )
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId",
                    )
                }
                else -> {
                    log.info(
                        "Henter ut oppgave med {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId),
                    )

                    if (!manuellOppgaveDTOList.firstOrNull()?.fnr.isNullOrEmpty()) {
                        val fnr = manuellOppgaveDTOList.first().fnr!!
                        log.info("Det finnes fnr på oppgavem oppgaveId $oppgaveId")

                        if (authorizationService.hasAccess(accessToken, fnr)) {
                            try {
                                val pdfPapirSykmelding =
                                    safDokumentClient.hentDokument(
                                        journalpostId = manuellOppgaveDTOList.first().journalpostId,
                                        dokumentInfoId =
                                            manuellOppgaveDTOList.first().dokumentInfoId ?: "",
                                        msgId = manuellOppgaveDTOList.first().sykmeldingId,
                                        accessToken = accessToken,
                                        sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId,
                                    )
                                if (pdfPapirSykmelding == null) {
                                    call.respond(HttpStatusCode.InternalServerError)
                                } else {
                                    log.info("oppretter responsen for oppgaveId $oppgaveId")
                                    val papirManuellOppgave =
                                        PapirManuellOppgave(
                                            fnr = manuellOppgaveDTOList.first().fnr,
                                            sykmeldingId =
                                                manuellOppgaveDTOList.first().sykmeldingId,
                                            oppgaveid = oppgaveId,
                                            pdfPapirSykmelding = pdfPapirSykmelding,
                                            papirSmRegistering =
                                                manuellOppgaveDTOList.first().papirSmRegistering,
                                            documents =
                                                listOf(
                                                    Document(
                                                        dokumentInfoId =
                                                            manuellOppgaveDTOList
                                                                .first()
                                                                .dokumentInfoId
                                                                ?: "",
                                                        tittel = "papirsykmelding",
                                                    ),
                                                ),
                                        )

                                    log.info("responsen for oppgaveId $oppgaveId er $papirManuellOppgave")
                                    call.respond(papirManuellOppgave)
                                }
                            } catch (safForbiddenException: SafForbiddenException) {
                                call.respond(
                                    HttpStatusCode.Forbidden,
                                    "Du har ikke tilgang til dokumentet i SAF",
                                )
                            } catch (safNotFoundException: SafNotFoundException) {
                                val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                                val journalpostId = manuellOppgaveDTOList.first().journalpostId
                                val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId

                                val loggingMeta =
                                    LoggingMeta(
                                        mottakId = sykmeldingId,
                                        dokumentInfoId = dokumentInfoId,
                                        msgId = sykmeldingId,
                                        sykmeldingId = sykmeldingId,
                                        journalpostId = journalpostId,
                                    )

                                sendTilGosysController.sendOppgaveTilGosys(
                                    oppgaveId,
                                    sykmeldingId,
                                    accessToken,
                                    loggingMeta,
                                )

                                auditlogg.info(
                                    AuditLogger()
                                        .createcCefMessage(
                                            fnr = manuellOppgaveDTOList.first().fnr,
                                            accessToken = accessToken,
                                            operation = AuditLogger.Operation.READ,
                                            requestPath = "/api/v1/oppgave/$oppgaveId",
                                            permit = AuditLogger.Permit.PERMIT,
                                        ),
                                )

                                call.respond(HttpStatusCode.Gone, "SENT_TO_GOSYS")
                            }
                        } else {
                            log.warn(
                                "Veileder har ikkje tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId),
                            )

                            sikkerlogg.info(
                                "Veileder har ikkje tilgang navEmail:" +
                                    "${JWT.decode(accessToken).claims["preferred_username"]!!.asString()}," +
                                    "requestPath \"/api/v1/oppgave/$oppgaveId\" {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId),
                            )

                            auditlogg.info(
                                AuditLogger()
                                    .createcCefMessage(
                                        fnr = manuellOppgaveDTOList.first().fnr,
                                        accessToken = accessToken,
                                        operation = AuditLogger.Operation.READ,
                                        requestPath = "/api/v1/oppgave/$oppgaveId",
                                        permit = AuditLogger.Permit.DENY,
                                    ),
                            )

                            call.respond(
                                HttpStatusCode.Forbidden,
                                "Veileder har ikke tilgang til oppgaven",
                            )
                        }
                    }
                }
            }
        }
    }
}
