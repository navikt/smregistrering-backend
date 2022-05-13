package no.nav.syfo.azuread.v2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import no.nav.syfo.Environment
import org.slf4j.LoggerFactory

class AzureAdV2Client(
    environment: Environment,
    private val httpClient: HttpClient,
    private val azureAdV2Cache: AzureAdV2Cache = AzureAdV2Cache()
) {
    private val azureAppClientId = environment.azureAppClientId
    private val azureAppClientSecret = environment.azureAppClientSecret
    private val azureTokenEndpoint = environment.azureTokenEndpoint

    /**
     * Returns a non-obo access token authenticated using app specific client credentials
     */
    suspend fun getAccessToken(
        scope: String
    ): String {
        return azureAdV2Cache.getAccessToken(scope)?.accessToken
            ?: getClientSecretAccessToken(scope).let {
                azureAdV2Cache.putValue(scope, it)
            }.accessToken
    }

    private suspend fun getClientSecretAccessToken(
        scope: String
    ): AzureAdV2Token {
        return getAccessTokenFromAzure(
            Parameters.build {
                append("client_id", azureAppClientId)
                append("client_secret", azureAppClientSecret)
                append("scope", scope)
                append("grant_type", "client_credentials")
            }
        ).toAzureAdV2Token()
    }

    /**
     * Returns a obo access token authenticated using provided user token
     */
    suspend fun getOnBehalfOfToken(
        token: String,
        scope: String
    ): String {
        return azureAdV2Cache.getOboToken(token, scope)?.accessToken
            ?: getOboAccessToken(token, scope).let {
                azureAdV2Cache.putValue(token, scope, it)
            }.accessToken
    }

    private suspend fun getOboAccessToken(
        token: String,
        scope: String
    ): AzureAdV2Token {
        return getAccessTokenFromAzure(
            Parameters.build {
                append("client_id", azureAppClientId)
                append("client_secret", azureAppClientSecret)
                append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", token)
                append("scope", scope)
                append("requested_token_use", "on_behalf_of")
            }
        ).toAzureAdV2Token()
    }

    private suspend fun getAccessTokenFromAzure(
        formParameters: Parameters
    ): AzureAdV2TokenResponse {
        return try {
            val response: HttpResponse = httpClient.post(azureTokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(formParameters))
            }
            response.body<AzureAdV2TokenResponse>()
        } catch (e: Exception) {
            log.error("Error while requesting AzureAdAccessToken", e)
            throw RuntimeException("Noe gikk galt ved henting av AAD-token", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AzureAdV2Client::class.java)
    }
}
