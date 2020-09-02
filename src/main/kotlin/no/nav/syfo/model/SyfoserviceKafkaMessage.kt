package no.nav.syfo.model

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

data class KafkaMessageMetadata(val sykmeldingId: String, val source: String)

data class SyfoserviceKafkaMessage(
    val metadata: KafkaMessageMetadata,
    val helseopplysninger: HelseOpplysningerArbeidsuforhet,
    val tilleggsdata: Tilleggsdata
)
