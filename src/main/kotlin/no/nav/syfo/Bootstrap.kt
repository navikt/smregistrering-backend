package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.controllers.AvvisPapirsykmeldingController
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.controllers.ReceivedSykmeldingController
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.db.Database
import no.nav.syfo.kafka.KafkaConsumers
import no.nav.syfo.kafka.KafkaProducers
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.pdf.PdfService
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.syfosmregister.SyfosmregisterService
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.sykmelding.SykmeldingJobRunner
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregisteringbackend")

val sikkerlogg = LoggerFactory.getLogger("securelog")

val auditlogg = LoggerFactory.getLogger("auditLogger")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val jwkProvider =
        JwkProviderBuilder(URI.create(env.jwkKeysUrl).toURL())
            .cached(10, Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val database = Database(env)

    val applicationState = ApplicationState()

    val manuellOppgaveDAO = ManuellOppgaveDAO(database)

    val kafkaConsumers = KafkaConsumers(env)
    val kafkaProducers = KafkaProducers(env)
    val httpClients = HttpClients(env)

    val sendtSykmeldingService = SendtSykmeldingService(databaseInterface = database)
    val authorizationService =
        AuthorizationService(httpClients.istilgangskontrollClient, httpClients.msGraphClient)
    val pdlService =
        PdlPersonService(httpClients.pdlClient, httpClients.azureAdV2Client, env.pdlScope)
    val sykmelderService = SykmelderService(httpClients.norskHelsenettClient, pdlService)
    val safJournalpostService =
        SafJournalpostService(env, httpClients.azureAdV2Client, httpClients.safJournalpostClient)
    val journalpostService = JournalpostService(httpClients.dokArkivClient, safJournalpostService)
    val oppgaveService = OppgaveService(httpClients.oppgaveClient)
    val syfosmregisterService =
        SyfosmregisterService(
            httpClients.azureAdV2Client,
            httpClients.syfoSmregisterClient,
            env.syfoSmregisterScope,
        )
    val pdfService =
        PdfService(
            manuellOppgaveDAO = manuellOppgaveDAO,
            dokumentClient = httpClients.safClient,
            authorizationService = authorizationService,
        )

    val avvisPapirsykmeldingController =
        AvvisPapirsykmeldingController(
            authorizationService,
            sykmelderService,
            manuellOppgaveDAO,
            oppgaveService,
            journalpostService,
        )
    val receivedSykmeldingController = ReceivedSykmeldingController(database, oppgaveService)
    val sendPapirsykmeldingController =
        SendPapirsykmeldingController(
            sykmelderService,
            pdlService,
            httpClients.smTssClient,
            httpClients.regelClient,
            authorizationService,
            sendtSykmeldingService,
            oppgaveService,
            journalpostService,
            manuellOppgaveDAO,
        )
    val sendTilGosysController =
        SendTilGosysController(authorizationService, manuellOppgaveDAO, oppgaveService)
    val ferdigstiltSykmeldingController =
        FerdigstiltSykmeldingController(
            manuellOppgaveDAO,
            httpClients.safClient,
            syfosmregisterService,
            authorizationService,
            safJournalpostService,
            receivedSykmeldingController,
        )

    val sykmeldingJobRunner =
        SykmeldingJobRunner(
            applicationState,
            sendtSykmeldingService,
            kafkaProducers.kafkaRecievedSykmeldingProducer,
        )

    val applicationEngine =
        createApplicationEngine(
            env,
            sendPapirsykmeldingController,
            applicationState,
            jwkProvider,
            manuellOppgaveDAO,
            httpClients.safClient,
            sendTilGosysController,
            avvisPapirsykmeldingController,
            ferdigstiltSykmeldingController,
            pdlService,
            sykmelderService,
            authorizationService,
            pdfService,
        )

    GlobalScope.launch(Dispatchers.IO) {
        sykmeldingJobRunner.startJobRunner()
        log.info("Started SykmeldingJobRunner")
    }

    startConsumer(
        applicationState,
        env.papirSmRegistreringTopic,
        kafkaConsumers.kafkaConsumerPapirSmRegistering,
        receivedSykmeldingController,
    )

    ApplicationServer(applicationEngine, applicationState).start()
}

@DelicateCoroutinesApi
fun startConsumer(
    applicationState: ApplicationState,
    topic: String,
    kafkaConsumerPapirSmRegistering: KafkaConsumer<String, String>,
    receivedSykmeldingController: ReceivedSykmeldingController,
) {
    GlobalScope.launch(Dispatchers.IO) {
        while (applicationState.ready) {
            try {
                log.info("Starting consuming topic $topic")
                kafkaConsumerPapirSmRegistering.subscribe(listOf(topic))
                while (applicationState.ready) {
                    kafkaConsumerPapirSmRegistering.poll(Duration.ofSeconds(10)).forEach {
                        consumerRecord ->
                        if (consumerRecord.value() == null) {
                            log.info(
                                "Mottatt tombstone for sykmelding med id ${consumerRecord.key()}"
                            )
                            receivedSykmeldingController.slettSykmelding(consumerRecord.key())
                        } else {
                            val receivedPapirSmRegistering: PapirSmRegistering =
                                objectMapper.readValue(consumerRecord.value())
                            val loggingMeta =
                                LoggingMeta(
                                    mottakId = receivedPapirSmRegistering.sykmeldingId,
                                    dokumentInfoId = receivedPapirSmRegistering.dokumentInfoId,
                                    msgId = receivedPapirSmRegistering.sykmeldingId,
                                    sykmeldingId = receivedPapirSmRegistering.sykmeldingId,
                                    journalpostId = receivedPapirSmRegistering.journalpostId,
                                )
                            receivedSykmeldingController.handleReceivedSykmelding(
                                papirSmRegistering = receivedPapirSmRegistering,
                                loggingMeta = loggingMeta,
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                log.error(
                    "Error running kafka consumer, unsubscribing and waiting 60 seconds for retry",
                    ex
                )
                kafkaConsumerPapirSmRegistering.unsubscribe()
                delay(60_000)
            }
        }
    }
}
