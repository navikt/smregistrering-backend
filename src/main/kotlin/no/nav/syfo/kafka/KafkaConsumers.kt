package no.nav.syfo.kafka

/*
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
*/
