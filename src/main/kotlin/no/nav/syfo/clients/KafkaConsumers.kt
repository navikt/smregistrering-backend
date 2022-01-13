package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaConsumers(env: Environment, vaultSecrets: VaultSecrets) {

    private val kafkaBaseConfig = KafkaUtils.getAivenKafkaConfig()
    private val kafkaBaseConfigOnPrem = loadBaseConfig(env, vaultSecrets)
    init {
        kafkaBaseConfig["auto.offset.reset"] = "earliest"
        kafkaBaseConfigOnPrem["auto.offset.reset"] = "none"
    }

    private val properties = kafkaBaseConfig
        .toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)

    private val onPremProperties = kafkaBaseConfigOnPrem.toConsumerConfig(
        "${env.applicationName}-consumer",
        valueDeserializer = StringDeserializer::class
    )

    val kafkaConsumerPapirSmRegistering = KafkaConsumer<String, String>(properties)
    val kafkaConsumerPapirSmRegisteringOnPrem = KafkaConsumer<String, String>(onPremProperties)
}
