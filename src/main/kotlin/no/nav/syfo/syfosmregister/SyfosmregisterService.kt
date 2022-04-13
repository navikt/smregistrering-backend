package no.nav.syfo.syfosmregister

import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.SyfosmregisterClient
import no.nav.syfo.log
import no.nav.syfo.syfosmregister.sykmelding.model.SykmeldingDTO

class SyfosmregisterService(
    private val accessTokenClientV2: AzureAdV2Client,
    private val syfosmregisterClient: SyfosmregisterClient,
    private val scope: String
) {

    // TODO: Oppdatert URL til nytt endepunkt for papirsykmeldinger i syfosmregister
    suspend fun hentSykmelding(sykmeldingId: String): SykmeldingDTO? {
        log.info("Fetching accesstoken for scope $scope")
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accessToken for smregister")
        }
        return syfosmregisterClient.getSykmelding(token = accessToken.accessToken, sykmeldingId = sykmeldingId)
    }
}
