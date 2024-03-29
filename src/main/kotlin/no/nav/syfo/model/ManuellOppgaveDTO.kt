package no.nav.syfo.model

import java.time.OffsetDateTime

data class ManuellOppgaveDTO(
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: OffsetDateTime?,
    val sykmeldingId: String,
    val oppgaveid: Int?,
    val ferdigstilt: Boolean,
    val papirSmRegistering: PapirSmRegistering?,
    var pdfPapirSykmelding: ByteArray?,
)
