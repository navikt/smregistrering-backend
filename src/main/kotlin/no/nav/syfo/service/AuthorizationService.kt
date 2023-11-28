package no.nav.syfo.service

import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.MSGraphClient

class AuthorizationService(
    private val istilgangskontrollClient: IstilgangskontrollClient,
    private val msGraphClient: MSGraphClient,
) {
    suspend fun hasAccess(accessToken: String, pasientFnr: String): Boolean {
        return istilgangskontrollClient
            .hasAccess(
                accessToken,
                pasientFnr,
            )
            .erGodkjent
    }

    suspend fun hasSuperuserAccess(accessToken: String, pasientFnr: String): Boolean {
        return istilgangskontrollClient
            .hasSuperuserAccess(
                accessToken,
                pasientFnr,
            )
            .erGodkjent
    }

    suspend fun getVeileder(accessToken: String): Veileder {
        val subject = msGraphClient.getSubjectFromMsGraph(accessToken)

        return Veileder(subject)
    }
}

class Veileder(
    val veilederIdent: String,
)
