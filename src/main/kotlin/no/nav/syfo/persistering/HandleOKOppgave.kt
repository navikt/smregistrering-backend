package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.FerdigStillOppgave
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.OppgaveStatus
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleOKOppgave(
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
    oppgaveId: Int
) {
    dokArkivClient.ferdigStillJournalpost(journalpostId, sykmeldingId, loggingMeta)
    kafkaRecievedSykmeldingProducer.producer.send(
        ProducerRecord(
            kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic,
            receivedSykmelding.sykmelding.id,
            receivedSykmelding
        )
    )
    log.info(
        "Message send to kafka {}, {}",
        kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic,
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
}

fun ferdigStillOppgave(oppgaveid: Int, oppgaveVersjon: Int) = FerdigStillOppgave(
    versjon = oppgaveVersjon,
    id = oppgaveid,
    status = OppgaveStatus.FERDIGSTILT
)