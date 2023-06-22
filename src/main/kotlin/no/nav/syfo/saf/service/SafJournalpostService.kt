package no.nav.syfo.saf.service

import java.lang.RuntimeException
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.saf.model.JournalpostResponse
import org.slf4j.LoggerFactory

/** Service for å fasilitere GrahpQL-oppslag mot SAF */
class SafJournalpostService(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val safJournalpostClient: SafJournalpostClient,
) {

    private val scope: String = environment.safScope

    companion object {
        private val log = LoggerFactory.getLogger(SafJournalpostService::class.java)
    }

    suspend fun erJournalfoert(journalpostId: String, token: String): Boolean {
        val journalPost = getJournalPostDokumentInfo(journalpostId, token)
        return erJournalfoert(journalPost.data.journalpost.journalstatus)
    }

    suspend fun getJournalPostDokumentInfo(
        journalpostId: String,
        token: String
    ): GraphQLResponse<JournalpostResponse> {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token, scope)

        val graphQLResponse = safJournalpostClient.getJournalpostMetadata(journalpostId, oboToken)

        if (graphQLResponse == null) {
            log.error("Kall til SAF feilet for $journalpostId")
            throw RuntimeException("Klarte ikke hente data fra SAF")
        }
        if (graphQLResponse.errors != null) {
            graphQLResponse.errors.forEach { log.error("Saf kastet error: {} ", it) }
        }

        if (graphQLResponse.data.journalpost.journalstatus == null) {
            log.error("Klarte ikke hente data fra SAF {}", journalpostId)
            throw RuntimeException("Klarte ikke hente data fra SAF")
        }
        return graphQLResponse
    }

    private fun erJournalfoert(journalstatus: String?): Boolean {
        return journalstatus?.let {
            it.equals("JOURNALFOERT", true) || it.equals("FERDIGSTILT", true)
        }
            ?: false
    }
}
