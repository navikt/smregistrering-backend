package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegisteringManuellt
import no.nav.syfo.model.Status
import no.nav.syfo.persistering.handleManuellOppgave
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
    aktoerIdClient: AktoerIdClient,
    serviceuserUsername: String,
    dokArkivClient: DokArkivClient,
    regelClient: RegelClient,
    kafkaValidationResultProducer: KafkaProducers.KafkaValidationResultProducer,
    kafkaManuelTaskProducer: KafkaProducers.KafkaManuelTaskProducer
) {
    route("/api/v1") {
        put("/sendPapirSykmeldingManuellOppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info(
                "Mottok eit kall til /api/v1/sendPapirSykmeldingManuellOppgave med {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId)
            )

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val smRegisteringManuellt: SmRegisteringManuellt = call.receive()

            when {
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

                        val aktoerIds = aktoerIdClient.getAktoerIds(
                            listOf(smRegisteringManuellt.sykmelderFnr, smRegisteringManuellt.pasientFnr),
                            serviceuserUsername,
                            loggingMeta
                        )

                        val patientIdents = aktoerIds[smRegisteringManuellt.pasientFnr]
                        val doctorIdents = aktoerIds[smRegisteringManuellt.sykmelderFnr]

                        if (patientIdents == null || patientIdents.feilmelding != null) {
                            log.error("Pasienten finnes ikkje i aktorregistert")
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                        if (doctorIdents == null || doctorIdents.feilmelding != null) {
                            log.error("Sykmelder finnes ikkje i aktorregistert")
                            call.respond(HttpStatusCode.InternalServerError)
                        }

                        val samhandlerInfo = kuhrsarClient.getSamhandler(smRegisteringManuellt.sykmelderFnr)
                        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                            samhandlerInfo,
                            loggingMeta
                        )
                        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                        val fellesformat = mapsmRegisteringManuelltTilFellesformat(
                            smRegisteringManuellt = smRegisteringManuellt,
                            pasientFnr = smRegisteringManuellt.pasientFnr,
                            sykmelderFnr = smRegisteringManuellt.sykmelderFnr,
                            sykmeldingId = sykmeldingId,
                            datoOpprettet = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet
                        )

                        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                        val msgHead = fellesformat.get<XMLMsgHead>()

                        val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = sykmeldingId,
                            pasientAktoerId = patientIdents!!.identer!!.first().ident,
                            legeAktoerId = doctorIdents!!.identer!!.first().ident,
                            msgId = sykmeldingId,
                            signaturDato = msgHead.msgInfo.genDate
                        )

                        val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = smRegisteringManuellt.sykmelderFnr,
                            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                            personNrLege = smRegisteringManuellt.sykmelderFnr,
                            navLogId = sykmeldingId,
                            msgId = sykmeldingId,
                            legekontorOrgNr = null,
                            legekontorOrgName = "",
                            legekontorHerId = null,
                            legekontorReshId = null,
                            mottattDato = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet ?: msgHead.msgInfo.genDate,
                            rulesetVersion = healthInformation.regelSettVersjon,
                            fellesformat = fellesformatMarshaller.toString(fellesformat),
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        log.info("Papirsykmelding manuell registering mappet til internt format uten feil {}", fields(loggingMeta))

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
                                        oppgaveId = oppgaveId
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
                                if (manuellOppgaveService.ferdigstillSmRegistering(oppgaveId) > 0) {
                                    handleManuellOppgave(
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
                                        validationResult = validationResult,
                                        kafkaManuelTaskProducer = kafkaManuelTaskProducer,
                                        kafkaValidationResultProducer = kafkaValidationResultProducer
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
                            else -> {
                                log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
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
