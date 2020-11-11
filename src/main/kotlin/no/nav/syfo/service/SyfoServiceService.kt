package no.nav.syfo.service

import java.time.LocalDateTime
import java.time.LocalTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.KafkaMessageMetadata
import no.nav.syfo.model.SyfoserviceKafkaMessage
import no.nav.syfo.model.Tilleggsdata
import org.apache.kafka.clients.producer.ProducerRecord

fun notifySyfoService(
    syfoserviceKafkaProducer: KafkaProducers.KafkaSyfoserviceProducer,
    sykmeldingId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet
) {
    val syfoserviceKafkaMessage = SyfoserviceKafkaMessage(
        metadata = KafkaMessageMetadata(sykmeldingId, source = "smregistrering-backend"),
        tilleggsdata = Tilleggsdata(
            ediLoggId = sykmeldingId,
            msgId = sykmeldingId,
            syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation),
            sykmeldingId = sykmeldingId
        ),
        helseopplysninger = healthInformation
    )

    try {
        syfoserviceKafkaProducer.producer.send(
            ProducerRecord(
                syfoserviceKafkaProducer.syfoserviceKafkaTopic,
                sykmeldingId,
                syfoserviceKafkaMessage
            )
        ).get()
    } catch (error: Exception) {
        log.error("Error producing message to Kafka", error)
        throw error
    }
}

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
    LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)
