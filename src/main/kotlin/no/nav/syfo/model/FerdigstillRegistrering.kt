package no.nav.syfo.model

import no.nav.syfo.service.Veileder

data class FerdigstillRegistrering(
    val oppgaveId: Int?,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val pasientFnr: String,
    val sykmeldingId: String,
    val sykmelder: Sykmelder,
    val navEnhet: String,
    val veileder: Veileder,
    val avvist: Boolean,
    val oppgave: Oppgave?,
)
