package no.nav.syfo.application.syfo

import no.nav.syfo.application.syfo.error.IdentNotFoundException
import no.nav.syfo.log
import java.lang.RuntimeException

class SyfoTilgangsKontrollService(
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

    suspend fun hentVeileder(accessToken: String): Veilder {
        val veilder = syfoTilgangsKontrollClient.hentVeilderIdentViaAzure(accessToken)
        if (veilder == null) {
            log.error("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
            throw IdentNotFoundException("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
        } else {
            return veilder
        }
    }
}
