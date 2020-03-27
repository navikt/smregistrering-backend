package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers(private val env: Environment, vaultSecrets: VaultSecrets) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()

        inner class KafkaRecievedSykmeldingProducer() {
            val producer = KafkaProducer<String, ReceivedSykmelding>(properties)

            val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic
        }
}
