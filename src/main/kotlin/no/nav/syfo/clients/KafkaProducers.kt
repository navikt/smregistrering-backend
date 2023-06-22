package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers(private val env: Environment) {
    private val kafkaBaseConfig = KafkaUtils.getAivenKafkaConfig()

    init {
        kafkaBaseConfig["auto.offset.reset"] = "none"
    }

    private val properties =
        kafkaBaseConfig.toProducerConfig(
            env.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )

    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()

    inner class KafkaRecievedSykmeldingProducer {
        val producer = KafkaProducer<String, ReceivedSykmelding>(properties)
        val sm2013AutomaticHandlingTopic = env.okSykmeldingTopic
    }
}
