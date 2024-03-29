package no.nav.syfo.syfosmregister

import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.syfosmregister.client.SyfosmregisterClient
import no.nav.syfo.syfosmregister.papirsykmelding.model.PapirsykmeldingDTO

class SyfosmregisterService(
    private val accessTokenClientV2: AzureAdV2Client,
    private val syfosmregisterClient: SyfosmregisterClient,
    private val scope: String,
) {

    suspend fun hentSykmelding(sykmeldingId: String): PapirsykmeldingDTO? {
        log.info("Fetching accesstoken for scope $scope")
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        return syfosmregisterClient.getSykmelding(token = accessToken, sykmeldingId = sykmeldingId)
    }
}
