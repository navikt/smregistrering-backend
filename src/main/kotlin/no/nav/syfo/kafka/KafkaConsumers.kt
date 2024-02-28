package no.nav.syfo.kafka

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaConsumers(env: Environment) {

    private val kafkaBaseConfig = KafkaUtils.getAivenKafkaConfig("papir-sm-registering-consumer")

    init {
        kafkaBaseConfig["auto.offset.reset"] = "none"
    }

    private val properties =
        kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer",
            valueDeserializer = StringDeserializer::class
        )

    val kafkaConsumerPapirSmRegistering = KafkaConsumer<String, String>(properties)
}
