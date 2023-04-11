package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.log
import no.nav.syfo.saf.model.GetJournalpostRequest
import no.nav.syfo.saf.model.GetJournalpostVariables
import no.nav.syfo.saf.model.JournalpostResponse

/**
 * GraphQL-klient for oppslag mot SAF
 */
class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String,
) {

    suspend fun getJournalpostMetadata(journalpostId: String, token: String): GraphQLResponse<JournalpostResponse>? {
        log.info("Henter journalpostmetadata for $journalpostId")
        val getJournalpostRequest = GetJournalpostRequest(query = graphQlQuery, variables = GetJournalpostVariables(journalpostId))
        return try {
            httpClient.post(basePath) {
                setBody(getJournalpostRequest)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("X-Correlation-ID", journalpostId)
                    append(HttpHeaders.ContentType, "application/json")
                }
            }.body<GraphQLResponse<JournalpostResponse>>()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av journalpostmetadata for journalpostid $journalpostId mot SAF: ${e.message}")
            null
        }
    }
}
