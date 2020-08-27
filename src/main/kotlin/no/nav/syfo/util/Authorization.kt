package no.nav.syfo.util

import no.nav.syfo.client.SyfoTilgangsKontrollClient

class Authorization(
    private val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    suspend fun hasAccess(accessToken: String, pasientFnr: String, cluster: String): Boolean {

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
}
