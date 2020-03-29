package no.nav.syfo.model

data class SmRegisteringManuellt(
    val pasientFnr: String,
    val sykmelderFnr: String,
    val perioder: List<Periode>,
    val medisinskVurdering: MedisinskVurdering
)
