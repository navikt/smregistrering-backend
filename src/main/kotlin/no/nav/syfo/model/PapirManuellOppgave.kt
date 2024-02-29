package no.nav.syfo.model

data class PapirManuellOppgave(
    val fnr: String?,
    val sykmeldingId: String,
    val oppgaveid: Int,
    var pdfPapirSykmelding: ByteArray,
    val papirSmRegistering: PapirSmRegistering?,
    val documents: List<Document>,
)

data class Document(
    val dokumentInfoId: String,
    val tittel: String,
)
