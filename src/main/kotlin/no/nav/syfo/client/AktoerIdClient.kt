package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.util.LoggingMeta

class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun getAktoerIds(
        personNumbers: List<String>,
        username: String,
        loggingMeta: LoggingMeta
    ): Map<String, IdentInfoResult> {
        return httpClient.get<HttpStatement>("$endpointUrl/identer") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Authorization", "Bearer ${oidcToken.access_token}")
                append("Nav-Consumer-Id", username)
                append("Nav-Call-Id", loggingMeta.msgId)
                append("Nav-Personidenter", personNumbers.joinToString(","))
            }
            parameter("gjeldende", "true")
            parameter("identgruppe", "AktoerId")
        }.execute().call.response.receive()
    }
}
