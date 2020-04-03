package no.nav.syfo.model

data class PapirManuellOppgave(
    val fnr: String?,
    val sykmeldingId: String,
    val oppgaveid: Int,
    var pdfPapirSykmelding: ByteArray?
)
