package no.nav.syfo.kafka

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers(private val env: Environment) {
    private val kafkaBaseConfig = KafkaUtils.getAivenKafkaConfig("ok-sykmelding-producer")

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
        val producer = KafkaProducer<String, ReceivedSykmeldingWithValidation>(properties)
        val sm2013AutomaticHandlingTopic = env.okSykmeldingTopic
    }
}
