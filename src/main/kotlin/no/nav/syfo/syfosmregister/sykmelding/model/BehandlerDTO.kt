package no.nav.syfo.syfosmregister.sykmelding.model

data class BehandlerDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val aktoerId: String?,
    val fnr: String?,
    val hpr: String?,
    val her: String?,
    val adresse: no.nav.syfo.syfosmregister.sykmelding.model.AdresseDTO,
    val tlf: String?
)
