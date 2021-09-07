package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult

class RegelClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String,
    private val client: HttpClient
) {
    @KtorExperimentalAPI
    suspend fun valider(sykmelding: ReceivedSykmelding, msgId: String): ValidationResult {
        return client.post("$endpointUrl/api/v2/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", msgId)
            }
            body = sykmelding
        }
    }
}
