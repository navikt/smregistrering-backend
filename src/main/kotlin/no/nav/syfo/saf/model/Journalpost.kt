package no.nav.syfo.saf.model

import no.nav.syfo.client.DokArkivClient

data class JournalpostResponse(
    val journalpost: Journalpost,
)

data class Journalpost(
    val journalstatus: String?,
    val dokumenter: List<DokArkivClient.DokumentInfo>,
)
