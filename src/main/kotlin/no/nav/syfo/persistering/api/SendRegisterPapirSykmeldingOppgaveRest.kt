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
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegisteringManuellt
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
    aktoerIdClient: AktoerIdClient
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
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (!manuellOppgaveDTOList.isNullOrEmpty()) {
                        // TODO hande the oppgave handleManuellOppgave()

                        val loggingMeta = LoggingMeta(
                            mottakId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            dokumentInfoId = manuellOppgaveDTOList.firstOrNull()?.dokumentInfoId,
                            msgId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            sykmeldingId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            journalpostId = manuellOppgaveDTOList.firstOrNull()?.journalpostId
                        )

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
                            sykmeldingId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            datoOpprettet = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet
                        )

                        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                        val msgHead = fellesformat.get<XMLMsgHead>()

                        val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            pasientAktoerId = "TODO",
                            legeAktoerId = "TODO",
                            msgId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            signaturDato = msgHead.msgInfo.genDate
                        )

                        val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = smRegisteringManuellt.sykmelderFnr,
                            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                            personNrLege = smRegisteringManuellt.sykmelderFnr,
                            navLogId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            msgId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                            legekontorOrgNr = null,
                            legekontorOrgName = "",
                            legekontorHerId = null,
                            legekontorReshId = null,
                            mottattDato = manuellOppgaveDTOList.firstOrNull()?.datoOpprettet ?: msgHead.msgInfo.genDate,
                            rulesetVersion = healthInformation.regelSettVersjon,
                            fellesformat = fellesformatMarshaller.toString(fellesformat),
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        log.info("Papir Sykmelding mappet til internt format uten feil {}", fields(loggingMeta))
                    } else {
                        log.warn(
                            "Henting av papir sykmelding manuell registering returente null {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
