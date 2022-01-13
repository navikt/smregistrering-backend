package no.nav.syfo.service

import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient

class AuthorizationService(
    private val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient,
    private val msGraphClient: MSGraphClient
) {
    suspend fun hasAccess(accessToken: String, pasientFnr: String): Boolean {
        val harTilgangTilOppgave =
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                accessToken,
                pasientFnr
            )?.harTilgang

        return harTilgangTilOppgave != null && harTilgangTilOppgave
    }
    suspend fun getVeileder(accessToken: String): Veileder {
        val subject = msGraphClient.getSubjectFromMsGraph(accessToken)

        return Veileder(subject)
    }
}

class Veileder(
    val veilederIdent: String
)
