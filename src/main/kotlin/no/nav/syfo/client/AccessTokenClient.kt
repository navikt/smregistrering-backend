package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.log
import no.nav.syfo.model.AadAccessToken

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient,
    private val aadCache: Cache<Map<String, String>, String>
) {
    private val mutex = Mutex()

    @Volatile
    private var tokenMap = HashMap<String, AadAccessToken>()

    suspend fun hentAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            (tokenMap[resource]
                ?.takeUnless { it.expires_on.isBefore(omToMinutter) }
                ?: run {
                    log.info("Henter nytt token fra Azure AD")
                    val response: AadAccessToken = httpClient.post(aadAccessTokenUrl) {
                        accept(ContentType.Application.Json)
                        method = HttpMethod.Post
                        body = FormDataContent(Parameters.build {
                            append("client_id", clientId)
                            append("resource", resource)
                            append("grant_type", "client_credentials")
                            append("client_secret", clientSecret)
                        })
                    }
                    tokenMap[resource] = response
                    log.debug("Har hentet accesstoken")
                    return@run response
                }).access_token
        }
    }

    suspend fun hentOnBehalfOfTokenForInnloggetBruker(accessToken: String, scope: String): String {
        aadCache.getIfPresent(mapOf(Pair(accessToken, scope)))?.let {
            log.debug("traff cache for AAD")
            return it
        }
        log.info("Henter OBO-token fra Azure AD")
        val response: AadAccessToken = httpClient.post(aadAccessTokenUrl) {
            accept(ContentType.Application.Json)
            method = HttpMethod.Post
            body = FormDataContent(Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("resource", scope)
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("requested_token_use", "on_behalf_of")
                append("assertion", accessToken)
                append("assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            })
        }
        log.info("Har hentet OBO-accesstoken")
        val oboToken = response.access_token
        aadCache.put(mapOf(Pair(accessToken, scope)), oboToken)
        return oboToken
    }
}
