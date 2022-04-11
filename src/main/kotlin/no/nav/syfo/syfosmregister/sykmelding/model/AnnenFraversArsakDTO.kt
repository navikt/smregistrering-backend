package no.nav.syfo.syfosmregister.sykmelding.model

data class AnnenFraversArsakDTO(
    val beskrivelse: String?,
    val grunn: List<AnnenFraverGrunnDTO>
)
