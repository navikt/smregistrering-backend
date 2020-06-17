package no.nav.syfo.util

import no.nav.syfo.client.SyfoTilgangsKontrollClient

suspend fun hasAccess(syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient, accessToken: String, pasientFnr: String, cluster: String): Boolean {

    return if (cluster == "dev-fss") {
        true
    } else {
        val harTilgangTilOppgave =
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                accessToken,
                pasientFnr
            )?.harTilgang

        harTilgangTilOppgave != null && harTilgangTilOppgave
    }
}