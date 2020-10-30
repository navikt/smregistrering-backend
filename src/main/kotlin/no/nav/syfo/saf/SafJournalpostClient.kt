package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.log
import no.nav.syfo.saf.model.GetJournalpostRequest
import no.nav.syfo.saf.model.GetJournalpostVariables
import no.nav.syfo.saf.model.JournalpostResponse

class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String
) {

    suspend fun getJournalpostMetadata(journalpostId: String, token: String): GraphQLResponse<JournalpostResponse>? {

        log.info("Henter journalpostmetadata for $journalpostId, basepath = $basePath")

        val getJournalpostRequest = GetJournalpostRequest(query = graphQlQuery, variables = GetJournalpostVariables(journalpostId))
        val httpResponse = httpClient.post<HttpStatement>(basePath) {
            body = getJournalpostRequest
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-Correlation-ID", journalpostId)
            header(HttpHeaders.ContentType, "application/json")
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                log.info("SAF svarte 200 OK")
                log.error(httpResponse.toString())
                httpResponse.call.response.receive()
            }
            else -> {
                log.error("Kall til SAF feilet {}", httpResponse)
                null
            }
        }
    }
}
