package no.nav.syfo.service

import no.nav.syfo.application.syfo.error.IdentNotFoundException
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.log

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

        if (subject == null) {
            log.error("Klarte ikke hente ut veilederident fra MS Graph")
            throw IdentNotFoundException("Klarte ikke hente ut veilederident fra MS Graph")
        } else {
            return Veileder(subject)
        }
    }
}

class Veileder(
    val veilederIdent: String
)
