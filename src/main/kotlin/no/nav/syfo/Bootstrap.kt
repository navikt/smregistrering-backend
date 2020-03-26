package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.clients.KafkaConsumers
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
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

    val applicationState = ApplicationState()

    val kafkaConsumers = KafkaConsumers(env, vaultSecrets)

    val applicationEngine = createApplicationEngine(env, applicationState)

    ApplicationServer(applicationEngine, applicationState).start()

    launchListeners(
        applicationState,
        env,
        kafkaConsumers
    )
}

@InternalAPI
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                StructuredArguments.fields(e.loggingMeta), e.cause
            )
        } finally {
            applicationState.alive = false
        }
    }

@InternalAPI
@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    kafkaConsumers: KafkaConsumers
) {
    createListener(applicationState) {
        val kafkaConsumerPapirSmRegistering = kafkaConsumers.kafkaConsumerPapirSmRegistering

        applicationState.ready = true

        kafkaConsumerPapirSmRegistering.subscribe(listOf(env.sm2013SmregistreringTopic))
        blockingApplicationLogic(
            applicationState,
            kafkaConsumerPapirSmRegistering
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedPapirSmRegistering: PapirSmRegistering = objectMapper.readValue(consumerRecord.value())
            val loggingMeta = LoggingMeta(
                mottakId = receivedPapirSmRegistering.sykmeldingId,
                dokumentInfoId = receivedPapirSmRegistering.dokumentInfoId,
                msgId = receivedPapirSmRegistering.sykmeldingId,
                sykmeldingId = receivedPapirSmRegistering.sykmeldingId,
                journalpostId = receivedPapirSmRegistering.journalpostId
            )

            handleRecivedMessage(receivedPapirSmRegistering, loggingMeta)
        }
        delay(100)
    }
}
