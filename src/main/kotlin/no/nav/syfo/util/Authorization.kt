package no.nav.syfo.util

import no.nav.syfo.client.SyfoTilgangsKontrollClient

class Authorization(
    private val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    suspend fun hasAccess(accessToken: String, pasientFnr: String): Boolean {
        val harTilgangTilOppgave =
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                accessToken,
                pasientFnr
            )?.harTilgang

        return harTilgangTilOppgave != null && harTilgangTilOppgave
    }
}
