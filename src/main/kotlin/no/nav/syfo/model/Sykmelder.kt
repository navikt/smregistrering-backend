package no.nav.syfo.model

import no.nav.syfo.client.Godkjenning

data class Sykmelder(
    val hprNummer: String?,
    val fnr: String?,
    val aktorId: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
    val godkjenninger: List<Godkjenning>?,
)
