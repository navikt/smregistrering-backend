package no.nav.syfo.model

import java.time.LocalDateTime

data class PapirSmRegistering(
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: LocalDateTime?,
    val sykmeldingId: String
)
