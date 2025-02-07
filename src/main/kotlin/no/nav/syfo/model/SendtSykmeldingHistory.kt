package no.nav.syfo.model

import java.time.LocalDateTime
import java.time.OffsetDateTime
import no.nav.syfo.kafka.SendtSykmeldingHistorySykDig

data class SendtSykmeldingHistory(
    val id: String,
    val sykmeldingId: String,
    val ferdigstiltAv: String,
    val datoFerdigstilt: LocalDateTime,
    val receivedSykmelding: ReceivedSykmelding,
) {
    fun mapToSykDig(): SendtSykmeldingHistorySykDig {
        return SendtSykmeldingHistorySykDig(
            id = id,
            sykmeldingId = sykmeldingId,
            ferdigstiltAv = ferdigstiltAv,
            datoFerdigstilt = datoFerdigstilt,
            timestamp = datoFerdigstilt.atOffset(OffsetDateTime.now().offset),
            receivedSykmelding = receivedSykmelding,
        )
    }
}
