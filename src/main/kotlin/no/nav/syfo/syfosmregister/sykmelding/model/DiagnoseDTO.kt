package no.nav.syfo.syfosmregister.sykmelding.model

data class DiagnoseDTO(
    val kode: String,
    val system: String,
    val tekst: String?
)
