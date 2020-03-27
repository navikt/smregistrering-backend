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
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendPapirSykmeldingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaRecievedSykmeldingProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        put("/sendPapirSykmeldingManuellOppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info(
                "Mottok eit kall til /api/v1/sendPapirSykmeldingManuellOppgave med {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId)
            )

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val receivedSykmelding: ReceivedSykmelding = call.receive()

            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (!manuellOppgaveDTOList.isNullOrEmpty()) {
                        // TODO hande the oppgave handleManuellOppgave()
                        /*
                        val samhandlerInfo = kuhrSarClient.getSamhandler(sykmelder.fnr)
                        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                                samhandlerInfo,
                                loggingMeta)
                        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                        val fellesformat = mapOcrFilTilFellesformat(
                                skanningmetadata = ocrFil,
                                fnr = fnr,
                                sykmelder = sykmelder,
                                sykmeldingId = sykmeldingId,
                                loggingMeta = loggingMeta)

                        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                        val msgHead = fellesformat.get<XMLMsgHead>()

                        val sykmelding = healthInformation.toSykmelding(
                                sykmeldingId = sykmeldingId,
                                pasientAktoerId = aktorId,
                                legeAktoerId = sykmelder.aktorId,
                                msgId = sykmeldingId,
                                signaturDato = msgHead.msgInfo.genDate
                        )

                        val receivedSykmelding = ReceivedSykmelding(
                                sykmelding = sykmelding,
                                personNrPasient = fnr,
                                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                personNrLege = sykmelder.fnr,
                                navLogId = sykmeldingId,
                                msgId = sykmeldingId,
                                legekontorOrgNr = null,
                                legekontorOrgName = "",
                                legekontorHerId = null,
                                legekontorReshId = null,
                                mottattDato = datoOpprettet ?: msgHead.msgInfo.genDate,
                                rulesetVersion = healthInformation.regelSettVersjon,
                                fellesformat = fellesformatMarshaller.toString(fellesformat),
                                tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        log.info("Sykmelding mappet til internt format uten feil {}", fields(loggingMeta))
                        */
                    } else {
                        log.warn(
                            "Henting av komplettManuellOppgave returente null {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
