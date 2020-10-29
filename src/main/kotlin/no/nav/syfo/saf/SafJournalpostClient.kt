package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.saf.model.GetJournalpostRequest
import no.nav.syfo.saf.model.GetJournalpostVariables
import no.nav.syfo.saf.model.Journalpost

class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String
) {

    suspend fun getJournalpostMetadata(journalpostId: String, token: String): GraphQLResponse<Journalpost> {
        val getJournalpostRequest = GetJournalpostRequest(query = graphQlQuery, variables = GetJournalpostVariables(journalpostId))
        return httpClient.post(basePath) {
            body = getJournalpostRequest
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-Correlation-ID", journalpostId)
            header(HttpHeaders.ContentType, "application/json")
        }
    }
}
