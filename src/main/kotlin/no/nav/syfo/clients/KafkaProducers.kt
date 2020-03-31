package no.nav.syfo.clients

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers(private val env: Environment, vaultSecrets: VaultSecrets) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties =
        kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val manualValidationProducerProperties =
        kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)

    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()
    val kafkaManuelTaskProducer = KafkaManuelTaskProducer()
    val kafkaValidationResultProducer = KafkaValidationResultProducer()

    inner class KafkaRecievedSykmeldingProducer() {
        val producer = KafkaProducer<String, ReceivedSykmelding>(properties)

        val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic
        val sm2013ManuellHandlingTopic = env.smpapirManualHandlingTopic
    }

    inner class KafkaManuelTaskProducer() {
        val producer = KafkaProducer<String, ProduceTask>(manualValidationProducerProperties)

        val sm2013ProduserOppgaveTopic = env.smProduserOppgaveTopic
    }

    inner class KafkaValidationResultProducer() {
        val producer = KafkaProducer<String, ValidationResult>(properties)

        val sm2013BehandlingsUtfallTopic = env.sm2013BehandlingsUtfallTopic
    }
}
