package no.nav.syfo.saf.service

import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.saf.SafJournalpostClient
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

/***
 * Service for å fasilitere GrahpQL-oppslag mot SAF
 */
class SafJournalpostService(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val safJournalpostClient: SafJournalpostClient
) {

    private val scope: String = environment.safScope

    companion object {
        private val log = LoggerFactory.getLogger(SafJournalpostService::class.java)
    }

    suspend fun erJournalfoert(journalpostId: String, token: String): Boolean {

        val oboToken = azureAdV2Client.getOnBehalfOfToken(token, scope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente OBO-token for SafJournalpostService")

        val graphQLResponse = safJournalpostClient.getJournalpostMetadata(journalpostId, oboToken)

        if (graphQLResponse == null) {
            log.error("Kall til SAF feilet for $journalpostId")
            throw RuntimeException("Klarte ikke hente data fra SAF")
        }
        if (graphQLResponse.errors != null) {
            graphQLResponse.errors.forEach {
                log.error("Saf kastet error: {} ", it)
            }
        }

        if (graphQLResponse.data.journalpost.journalstatus == null) {
            log.error("Klarte ikke hente data fra SAF {}", journalpostId)
            throw RuntimeException("Klarte ikke hente data fra SAF")
        }

        return erJournalfoert(graphQLResponse.data.journalpost.journalstatus)
    }

    private fun erJournalfoert(journalstatus: String?): Boolean {
        return journalstatus?.let {
            it.equals("JOURNALFOERT", true) || it.equals("FERDIGSTILT", true)
        } ?: false
    }
}
