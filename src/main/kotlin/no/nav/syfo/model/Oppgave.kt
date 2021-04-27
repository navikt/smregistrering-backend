package no.nav.syfo.model

import java.time.LocalDate

data class Oppgave(
    val id: Int?,
    val versjon: Int? = null,
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String?,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
    val status: String? = null,
    val mappeId: Int? = null
)

data class FerdigstillOppgave(
    val id: Int,
    val versjon: Int,
    val status: OppgaveStatus,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Int?
)

enum class OppgaveStatus(val status: String) {
    FERDIGSTILT("FERDIGSTILT")
}
