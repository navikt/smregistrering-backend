package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Veileder
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleOKOppgave(
    receivedSykmelding: ReceivedSykmelding,
    kafkaRecievedSykmeldingProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    loggingMeta: LoggingMeta,
    syfoserviceKafkaProducer: KafkaProducers.KafkaSyfoserviceProducer,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    accessToken: String,
    sykmeldingId: String,
    journalpostId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    oppgaveId: Int,
    veileder: Veileder,
    sykmelder: Sykmelder,
    navEnhet: String
) {

    if (!safJournalpostService.erJournalfoert(journalpostId = journalpostId, token = accessToken)) {
        dokArkivClient.oppdaterOgFerdigstillJournalpost(
            journalpostId,
            receivedSykmelding.personNrPasient,
            sykmeldingId,
            sykmelder,
            loggingMeta,
            navEnhet
        )
    } else {
        log.info("Hopper over oppdaterOgFerdigstillJournalpost, journalpostId $journalpostId er allerede journalført")
    }

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
        syfoserviceKafkaProducer = syfoserviceKafkaProducer,
        ediLoggId = sykmeldingId,
        sykmeldingId = receivedSykmelding.sykmelding.id,
        msgId = sykmeldingId,
        healthInformation = healthInformation
    )
    log.info("Message send to syfoService, {}", fields(loggingMeta))

    val oppgave = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId)

    if (OppgaveStatus.FERDIGSTILT.name != oppgave.status) {
        val ferdigstillOppgave = FerdigstillOppgave(
            versjon = oppgave.versjon!!,
            id = oppgaveId,
            status = OppgaveStatus.FERDIGSTILT,
            tildeltEnhetsnr = navEnhet,
            tilordnetRessurs = veileder.veilederIdent
        )

        val ferdigstiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)
        log.info(
            "Ferdigstiller oppgave med {}, {}",
            keyValue("oppgaveId", ferdigstiltOppgave.id),
            fields(loggingMeta)
        )
    } else {
        log.info("Hopper over ferdigstillOppgave, oppgaveId $oppgaveId er allerede ${oppgave.status}")
    }
}
