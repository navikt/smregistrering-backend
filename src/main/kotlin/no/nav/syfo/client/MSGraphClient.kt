package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import java.io.Serializable
import java.util.concurrent.TimeUnit

class MSGraphClient(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val oboScope: String = environment.msGraphApiScope,
    msGraphApiUrl: String = environment.msGraphApiUrl
) {

    private val msGraphApiAccountNameQuery = "$msGraphApiUrl/me/?\$select=onPremisesSamAccountName"

    val subjectCache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<String, String>()

    suspend fun getSubjectFromMsGraph(accessToken: String): String {

        subjectCache.getIfPresent(accessToken)?.let {
            log.debug("Traff subject cache for MSGraph")
            return it
        }

        return try {
            val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = oboScope)
            val subject = callMsGraphApi(oboToken)
            subjectCache.put(accessToken, subject)
            subject
        } catch (e: Exception) {
            throw RuntimeException("Klarte ikke hente veileder-ident fra MSGraph: ", e)
        }
    }

    private suspend fun callMsGraphApi(oboToken: String): String {
        try {
            return httpClient.get(msGraphApiAccountNameQuery) {
                headers {
                    append("Authorization", "Bearer $oboToken")
                }
            }.body<GraphResponse>().onPremisesSamAccountName
        } catch (e: Exception) {
            throw RuntimeException("Noe gikk galt ved henting av veilederIdent fra Ms Graph", e)
        }
    }
}

data class GraphResponse(val onPremisesSamAccountName: String) : Serializable
