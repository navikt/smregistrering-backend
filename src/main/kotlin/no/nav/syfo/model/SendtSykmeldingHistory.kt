package no.nav.syfo.model

import java.time.OffsetDateTime

data class SendtSykmeldingHistory(
    val id: String,
    val sykmeldingId: String,
    val ferdigstiltAv: String,
    val datoFerdigstilt: OffsetDateTime,
    val receivedSykmelding: ReceivedSykmelding
)
