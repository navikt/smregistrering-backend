package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.finnFristForFerdigstillingAvOppgave
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleManuellOppgave(
    receivedSykmelding: ReceivedSykmelding,
    kafkaRecievedSykmeldingProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    loggingMeta: LoggingMeta,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    sykmeldingId: String,
    journalpostId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    oppgaveId: Int,
    validationResult: ValidationResult,
    kafkaValidationResultProducer: KafkaProducers.KafkaValidationResultProducer,
    kafkaManuelTaskProducer: KafkaProducers.KafkaManuelTaskProducer
) {

    dokArkivClient.ferdigStillJournalpost(journalpostId, sykmeldingId, loggingMeta)

    kafkaRecievedSykmeldingProducer.producer.send(
        ProducerRecord(
            kafkaRecievedSykmeldingProducer.sm2013ManuellHandlingTopic,
            receivedSykmelding.sykmelding.id,
            receivedSykmelding
        )
    )
    log.info(
        "Message send to kafka {}, {}",
        kafkaRecievedSykmeldingProducer.sm2013ManuellHandlingTopic,
        fields(loggingMeta)
    )

    notifySyfoService(
        session = session, receiptProducer = syfoserviceProducer, ediLoggId = sykmeldingId,
        sykmeldingId = receivedSykmelding.sykmelding.id, msgId = sykmeldingId, healthInformation = healthInformation
    )
    log.info("Message send to syfoService, {}", fields(loggingMeta))

    val oppgaveVersjon = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId).versjon

    val ferdigStillOppgave = ferdigStillOppgave(oppgaveId, oppgaveVersjon)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, sykmeldingId)
    log.info(
        "Ferdigstilter oppgave med {}, {}",
        keyValue("oppgaveId", oppgaveResponse.id),
        fields(loggingMeta)
    )

    log.info("Sending manuell oppgave to syfosmoppgave {}", fields(loggingMeta))
    opprettOppgave(kafkaManuelTaskProducer, receivedSykmelding, validationResult, loggingMeta)

    sendValidationResult(validationResult, kafkaValidationResultProducer, receivedSykmelding, loggingMeta)
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaValidationResultProducer: KafkaProducers.KafkaValidationResultProducer,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    kafkaValidationResultProducer.producer.send(
        ProducerRecord(
            kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic,
            receivedSykmelding.sykmelding.id,
            validationResult
        )
    )
    log.info(
        "Validation results send to kafka {}, {}",
        kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic,
        fields(loggingMeta)
    )
}

fun opprettOppgave(
    kafkaManuelTaskProducer: KafkaProducers.KafkaManuelTaskProducer,
    receivedSykmelding: ReceivedSykmelding,
    results: ValidationResult,
    loggingMeta: LoggingMeta
) {
    kafkaManuelTaskProducer.producer.send(
        ProducerRecord(
            kafkaManuelTaskProducer.sm2013ProduserOppgaveTopic,
            receivedSykmelding.sykmelding.id,
            ProduceTask().apply {
                messageId = receivedSykmelding.msgId
                aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                tildeltEnhetsnr = ""
                opprettetAvEnhetsnr = "9999"
                behandlesAvApplikasjon = "FS22" // Gosys
                orgnr = receivedSykmelding.legekontorOrgNr ?: ""
                beskrivelse =
                    "Manuell behandling av sykmelding grunnet følgende regler: ${results.ruleHits.joinToString(
                        ", ",
                        "(",
                        ")"
                    ) { it.messageForSender }}"
                temagruppe = "ANY"
                tema = "SYM"
                behandlingstema = "ANY"
                oppgavetype = "BEH_EL_SYM"
                behandlingstype = "ANY"
                mappeId = 1
                aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(
                    finnFristForFerdigstillingAvOppgave(
                        LocalDate.now().plusDays(4)
                    )
                )
                prioritet = PrioritetType.NORM
                metadata = mapOf()
            })
    )

    log.info("Message sendt to topic: ${kafkaManuelTaskProducer.sm2013ProduserOppgaveTopic} {}", fields(loggingMeta))
}
