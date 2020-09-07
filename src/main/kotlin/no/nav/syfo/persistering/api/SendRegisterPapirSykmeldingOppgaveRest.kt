package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.syfo.AuthorizationService
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegisteringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.handleOKOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.service.mapsmRegisteringManuelltTilFellesformat
import no.nav.syfo.service.toSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.toString

@KtorExperimentalAPI
fun Route.sendPapirSykmeldingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaRecievedSykmeldingProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveClient: OppgaveClient,
    kuhrsarClient: SarClient,
    dokArkivClient: DokArkivClient,
    regelClient: RegelClient,
    pdlService: PdlPersonService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        put("/sendPapirSykmeldingManuellOppgave") {
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            log.info(
                "Mottok eit kall til /api/v1/sendPapirSykmeldingManuellOppgave med {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId)
            )

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val userToken = call.request.headers[HttpHeaders.Authorization]!!
            val callId = UUID.randomUUID().toString()

            val smRegisteringManuell: SmRegisteringManuell = call.receive()

            when {
                oppgaveId == null -> {
                    log.warn("Mangler oppgaveid queryParameters")
                    call.respond(HttpStatusCode.BadRequest)
                }
                accessToken == null -> {
                    log.warn("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (!manuellOppgaveDTOList.isNullOrEmpty()) {
                        val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                        val journalpostId = manuellOppgaveDTOList.first().journalpostId
                        val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId

                        val loggingMeta = LoggingMeta(
                            mottakId = sykmeldingId,
                            dokumentInfoId = dokumentInfoId,
                            msgId = sykmeldingId,
                            sykmeldingId = sykmeldingId,
                            journalpostId = journalpostId
                        )

                        if (authorizationService.hasAccess(accessToken, smRegisteringManuell.pasientFnr)) {

                            log.info("Henter sykmelder fra PDL {} ", loggingMeta)
                            val sykmelder = pdlService.getPdlPerson(fnr = smRegisteringManuell.sykmelderFnr, userToken = userToken, callId = callId)

                            log.info("Henter pasient fra PDL {} ", loggingMeta)
                            val pasient = pdlService.getPdlPerson(fnr = smRegisteringManuell.pasientFnr, userToken = userToken, callId = callId)

                            if (pasient.fnr == null) {
                                log.error("Pasientens fnr finnes ikke i PDL")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                            if (sykmelder.aktorId == null || sykmelder.fnr == null) {
                                log.error("Sykmelders aktørId eller fnr finnes ikke i PDL")
                                call.respond(HttpStatusCode.InternalServerError)
                            }

                            val samhandlerInfo = kuhrsarClient.getSamhandler(smRegisteringManuell.sykmelderFnr)
                            val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                                samhandlerInfo,
                                loggingMeta
                            )
                            val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                            val fellesformat = mapsmRegisteringManuelltTilFellesformat(
                                smRegisteringManuell = smRegisteringManuell,
                                pdlPasient = pasient,
                                sykmelderFnr = sykmelder.fnr!!,
                                pdlSykmelder = sykmelder,
                                sykmeldingId = sykmeldingId,
                                datoOpprettet = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet?.toLocalDateTime()
                            )

                            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                            val msgHead = fellesformat.get<XMLMsgHead>()

                            val sykmelding = healthInformation.toSykmelding(
                                sykmeldingId = sykmeldingId,
                                pasientAktoerId = pasient.fnr!!,
                                legeAktoerId = sykmelder.fnr,
                                msgId = sykmeldingId,
                                signaturDato = msgHead.msgInfo.genDate
                            )

                            val receivedSykmelding = ReceivedSykmelding(
                                sykmelding = sykmelding,
                                personNrPasient = smRegisteringManuell.sykmelderFnr,
                                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                personNrLege = smRegisteringManuell.sykmelderFnr,
                                navLogId = sykmeldingId,
                                msgId = sykmeldingId,
                                legekontorOrgNr = null,
                                legekontorOrgName = "",
                                legekontorHerId = null,
                                legekontorReshId = null,
                                mottattDato = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet?.toLocalDateTime()
                                    ?: msgHead.msgInfo.genDate,
                                rulesetVersion = healthInformation.regelSettVersjon,
                                fellesformat = fellesformatMarshaller.toString(fellesformat),
                                tssid = samhandlerPraksis?.tss_ident ?: ""
                            )

                            log.info(
                                "Papirsykmelding manuell registering mappet til internt format uten feil {}",
                                fields(loggingMeta)
                            )

                            val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
                            log.info(
                                "Resultat: {}, {}, {}",
                                StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                                StructuredArguments.keyValue(
                                    "ruleHits",
                                    validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                                fields(loggingMeta)
                            )

                            when (validationResult.status) {
                                Status.OK -> {

                                    val veileder = authorizationService.getVeileder(accessToken)

                                    if (manuellOppgaveService.ferdigstillSmRegistering(oppgaveId) > 0) {
                                        handleOKOppgave(
                                            receivedSykmelding = receivedSykmelding,
                                            kafkaRecievedSykmeldingProducer = kafkaRecievedSykmeldingProducer,
                                            loggingMeta = loggingMeta,
                                            session = session,
                                            syfoserviceProducer = syfoserviceProducer,
                                            oppgaveClient = oppgaveClient,
                                            dokArkivClient = dokArkivClient,
                                            sykmeldingId = sykmeldingId,
                                            journalpostId = journalpostId,
                                            healthInformation = healthInformation,
                                            oppgaveId = oppgaveId,
                                            veileder = veileder
                                        )
                                        call.respond(HttpStatusCode.NoContent)
                                    } else {
                                        log.error(
                                            "Ferdigstilling av papirsykmeldinger manuell registering i db feilet {}",
                                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                        )
                                        call.respond(HttpStatusCode.InternalServerError)
                                    }
                                }
                                Status.MANUAL_PROCESSING -> {
                                    log.info(
                                        "Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                    )
                                    call.respond(HttpStatusCode.BadRequest, validationResult)
                                }
                                else -> {
                                    log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                                    call.respond(HttpStatusCode.InternalServerError)
                                }
                            }
                        } else {
                            log.warn(
                                "Veileder har ikkje tilgang, {}, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId), fields(loggingMeta)
                            )
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    } else {
                        log.warn(
                            "Henting av papirsykmeldinger manuell registering returente null {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
