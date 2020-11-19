package no.nav.syfo.model

import no.nav.syfo.pdl.model.Navn

data class PapirManuellOppgave(
    val fnr: String?,
    val navn: Navn,
    val sykmeldingId: String,
    val oppgaveid: Int,
    var pdfPapirSykmelding: ByteArray,
    val papirSmRegistering: PapirSmRegistering?
)
