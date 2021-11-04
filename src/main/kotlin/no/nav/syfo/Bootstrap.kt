package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import java.net.URL
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.clients.KafkaConsumers
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelding.SykmeldingJobRunner
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.Unbounded
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregisteringbackend")

@InternalAPI
@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets(
        serviceuserUsername = getFileAsString(env.serviceuserUsernamePath),
        serviceuserPassword = getFileAsString(env.serviceuserPasswordPath)
    )

    val jwkProvider = JwkProviderBuilder(URL(env.jwkKeysUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()

    val manuellOppgaveService = ManuellOppgaveService(database)

    val kafkaConsumers = KafkaConsumers(env, vaultSecrets)
    val kafkaProducers = KafkaProducers(env, vaultSecrets)
    val httpClients = HttpClients(env, vaultSecrets)

    val sykmeldingJobService = SykmeldingJobService(databaseInterface = database)
    val sykmeldingJobRunner = SykmeldingJobRunner(applicationState, sykmeldingJobService, kafkaProducers.kafkaRecievedSykmeldingProducer, kafkaProducers.kafkaSyfoserviceProducer)

    val applicationEngine = createApplicationEngine(sykmeldingJobService,
        env,
        applicationState,
        jwkProvider,
        manuellOppgaveService,
        httpClients.safClient,
        httpClients.oppgaveClient,
        httpClients.sarClient,
        httpClients.dokArkivClient,
        httpClients.safJournalpostService,
        httpClients.regelClient,
        httpClients.pdlService,
        httpClients.sykmelderService,
        AuthorizationService(httpClients.syfoTilgangsKontrollClient, httpClients.msGraphClient)
    )

    ApplicationServer(applicationEngine, applicationState).start()

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    applicationState.ready = true

    GlobalScope.launch {
        sykmeldingJobRunner.startJobRunner()
        log.info("Started SykmeldingJobRunner")
    }

    val kafkaConsumerPapirSmRegistering = kafkaConsumers.kafkaConsumerPapirSmRegistering

    startConsumer(
        applicationState,
        env,
        kafkaConsumerPapirSmRegistering,
        database,
        httpClients.oppgaveClient
    )
}

@KtorExperimentalAPI
fun startConsumer(
    applicationState: ApplicationState,
    env: Environment,
    kafkaConsumerPapirSmRegistering: KafkaConsumer<String, String>,
    database: Database,
    oppgaveClient: OppgaveClient
) {
    GlobalScope.launch(Dispatchers.Unbounded) {
        while (applicationState.ready) {
            try {
                log.info("Starting consuming topic ${env.sm2013SmregistreringTopic}")
                kafkaConsumerPapirSmRegistering.subscribe(listOf(env.sm2013SmregistreringTopic))
                while (applicationState.ready) {
                    kafkaConsumerPapirSmRegistering.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
                        val receivedPapirSmRegistering: PapirSmRegistering =
                            objectMapper.readValue(consumerRecord.value())
                        val loggingMeta = LoggingMeta(
                            mottakId = receivedPapirSmRegistering.sykmeldingId,
                            dokumentInfoId = receivedPapirSmRegistering.dokumentInfoId,
                            msgId = receivedPapirSmRegistering.sykmeldingId,
                            sykmeldingId = receivedPapirSmRegistering.sykmeldingId,
                            journalpostId = receivedPapirSmRegistering.journalpostId
                        )
                        handleRecivedMessage(receivedPapirSmRegistering, database, oppgaveClient, loggingMeta)
                    }
                    delay(1)
                }
            } catch (ex: Exception) {
                log.error("Error running kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                kafkaConsumerPapirSmRegistering.unsubscribe()
                delay(10_000)
            }
        }
    }
}
