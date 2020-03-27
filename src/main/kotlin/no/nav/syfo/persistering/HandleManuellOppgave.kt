package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.client.FerdigStillOppgave
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.OppgaveStatus
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleManuellOppgave(
    manuellOppgave: ManuellOppgaveDTO,
    receivedSykmelding: ReceivedSykmelding,
    sm2013AutomaticHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    loggingMeta: LoggingMeta,
    syfoserviceQueueName: String,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveClient: OppgaveClient
) {
    val fellesformat = fellesformatUnmarshaller.unmarshal(
        StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat

    // TODO remove notifySyfoService, when we no longer uses syfoService app to show sykmeldinger
    notifySyfoService(
        session = session,
        receiptProducer = syfoserviceProducer,
        ediLoggId = receivedSykmelding.navLogId,
        sykmeldingId = receivedSykmelding.sykmelding.id,
        msgId = receivedSykmelding.msgId,
        healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    )
    log.info("Melding sendt til syfoService kø {}, {}", syfoserviceQueueName, fields(loggingMeta))

    kafkaproducerreceivedSykmelding.send(
        ProducerRecord(
            sm2013AutomaticHandlingTopic,
            receivedSykmelding.sykmelding.id,
            receivedSykmelding)
    )
    log.info("Melding sendt til kafka topic {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))

    val oppgaveVersjon = oppgaveClient.hentOppgave(manuellOppgave.oppgaveid, receivedSykmelding.msgId).versjon

    val ferdigStillOppgave = ferdigStillOppgave(manuellOppgave, oppgaveVersjon)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, receivedSykmelding.msgId)
    log.info(
        "Ferdigstilter oppgave med {}, {}",
        keyValue("oppgaveId", oppgaveResponse.id),
        fields(loggingMeta)
    )
}

fun ferdigStillOppgave(manuellOppgave: ManuellOppgaveDTO, oppgaveVersjon: Int) = FerdigStillOppgave(
    versjon = oppgaveVersjon,
    id = manuellOppgave.oppgaveid,
    status = OppgaveStatus.FERDIGSTILT
)
