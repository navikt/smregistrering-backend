package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class AzureGraphService @KtorExperimentalAPI constructor(
    private val httpClient: HttpClient,
    private val accessTokenClient: AccessTokenClient,
    private val resource: String
) {
    suspend fun getNavident(
        accessToken: String,
        loggingMeta: LoggingMeta
    ): String? =
        retry("get_navident") {

            log.debug("Forsøker å hente navident for bruker fra ms graph {}", fields(loggingMeta))
            val accessTokenOnBehalfOf = accessTokenClient.hentAccessTokenOnBehalfOf(resource, accessToken)

            val receive =
                httpClient.get<HttpStatement>("https://graph.microsoft.com/v1.0/me?\$select=onPremisesSamAccountName") {

                    accept(ContentType.Application.Json)
                    headers {
                        append("Authorization", "Bearer $accessTokenOnBehalfOf")
                    }
                }.execute().call.response.receive<Map<String, String>>()

            when {
                receive.containsKey("onPremisesSamAccountName") -> {
                    log.debug("Hentet navident for bruker {} fra ms graph {}", receive["onPremisesSamAccountName"], fields(loggingMeta))
                    receive["onPremisesSamAccountName"]
                }
                else -> null
            }

        }
}