package no.nav.syfo.application.syfo

import no.nav.syfo.application.syfo.error.IdentNotFoundException
import no.nav.syfo.log

class AuthorizationService(
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
    suspend fun getVeileder(accessToken: String): Veilder {
        val veilder = syfoTilgangsKontrollClient.hentVeilderIdentViaAzure(accessToken)
        if (veilder == null) {
            log.error("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
            throw IdentNotFoundException("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
        } else {
            return veilder
        }
    }
}
