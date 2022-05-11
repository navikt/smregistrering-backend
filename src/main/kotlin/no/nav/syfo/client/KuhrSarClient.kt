package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.metrics.SAMHANDLERPRAKSIS_FOUND_COUNTER
import no.nav.syfo.metrics.SAMHANDLERPRAKSIS_NOT_FOUND_COUNTER
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.SamhandlerPraksis

class SarClient(
    private val endpointUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val resourceId: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String): List<Samhandler> {
        val accessToken = azureAdV2Client.getAccessToken(resourceId)
        return httpClient.get("$endpointUrl/sar/rest/v2/samh") {
            accept(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            parameter("ident", ident)
        }.body()
    }
}

fun findBestSamhandlerPraksis(
    samhandlere: List<Samhandler>
): SamhandlerPraksis? {
    return getAktivOrInaktivSamhandlerPraksis(samhandlere).also {
        updateSamhandlerMetrics(it)
    }
}

private fun updateSamhandlerMetrics(it: SamhandlerPraksis?) {
    when (it) {
        null -> {
            SAMHANDLERPRAKSIS_NOT_FOUND_COUNTER.inc()
        }
        else -> {
            SAMHANDLERPRAKSIS_FOUND_COUNTER.labels(it.samh_praksis_type_kode).inc()
        }
    }
}

private fun getAktivOrInaktivSamhandlerPraksis(samhandlere: List<Samhandler>): SamhandlerPraksis? {
    val samhandlerPraksis = samhandlere.flatMap { it.samh_praksis }.groupBy { it.samh_praksis_status_kode }
    return samhandlerPraksis["aktiv"]?.firstOrNull() ?: samhandlerPraksis["inaktiv"]?.firstOrNull()
}
