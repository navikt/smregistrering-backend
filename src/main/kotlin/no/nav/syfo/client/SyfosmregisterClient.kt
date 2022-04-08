package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.log
import no.nav.syfo.model.Sykmelding

class SyfosmregisterClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getSykmelding(token: String, sykmeldingId: String): Sykmelding? {
        try {
            return httpClient.get("$endpointUrl/api/v2/sykmelding/$sykmeldingId") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $token")
                }
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall getSykmelding $sykmeldingId", e)
            throw e
        }
    }
}
