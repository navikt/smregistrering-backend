package no.nav.syfo.model

import java.time.LocalDateTime
import no.nav.syfo.util.LoggingMeta

data class PapirSmRegistering(
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: LocalDateTime?,
    val loggingMeta: LoggingMeta,
    val sykmeldingId: String
)
