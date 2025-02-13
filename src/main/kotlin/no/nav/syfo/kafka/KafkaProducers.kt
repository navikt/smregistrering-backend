package no.nav.syfo.kafka

import java.time.LocalDateTime
import java.time.OffsetDateTime
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.model.ManuellOppgaveDTOSykDig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers(private val env: Environment) {
    private val kafkaBaseConfig = KafkaUtils.getAivenKafkaConfig("migration-producer")

    init {
        kafkaBaseConfig["auto.offset.reset"] = "none"
    }

    private val properties =
        kafkaBaseConfig.toProducerConfig(
            env.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )

    inner class KafkaSmregMigrationProducer {
        val producer = KafkaProducer<String, MigrationObject>(properties)
        val sm2013AutomaticHandlingTopic = env.smregMigrationTopic
    }
}

data class MigrationObject(
    val sykmeldingId: String,
    val manuellOppgave: ManuellOppgaveDTOSykDig,
    val sendtSykmeldingHistory: List<SendtSykmeldingHistorySykDig>?,
)

data class SendtSykmeldingHistorySykDig(
    val sykmeldingId: String,
    val ferdigstiltAv: String?,
    val datoFerdigstilt: LocalDateTime?,
    val timestamp: OffsetDateTime,
    val receivedSykmelding: ReceivedSykmelding,
)

data class ReceivedSykmeldingWithTimestamp(
    val receivedSykmelding: ReceivedSykmelding,
    val timestamp: OffsetDateTime
)
