package no.nav.syfo.pdl.service

import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.client.model.Identliste
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlResponse

fun getPdlResponse(): GraphQLResponse<PdlResponse> {
    return GraphQLResponse<PdlResponse>(PdlResponse(
            hentPerson = HentPerson(listOf(Navn("fornavn", null, "etternavn"))),
            hentIdenter = Identliste(listOf(IdentInformasjon(ident = "987654321", gruppe = "AKTORID", historisk = false)))
    ), errors = null)
}
