package no.nav.syfo.saf.service

import no.nav.syfo.saf.SafJournalpostClient
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class SafJournalpostService(
    val safJournalpostClient: SafJournalpostClient
) {
    companion object {
        private val log = LoggerFactory.getLogger(SafJournalpostService::class.java)
    }

    suspend fun erJournalfoert(journalpostId: String, token: String): Boolean {
        val graphQLResponse = safJournalpostClient.getJournalpostMetadata(journalpostId, token)
        if (graphQLResponse.errors != null) {
            graphQLResponse.errors.forEach {
                log.error("Saf kastet error: {} ", it)
            }
        }

        if (graphQLResponse.data.journalstatus == null) {
            log.error("Klarte ikke hente data fra SAF {}", journalpostId)
            throw RuntimeException("Klarte ikke hente data fra SAF")
        }

        return erJournalfoert(graphQLResponse.data.journalstatus)
    }

    private fun erJournalfoert(journalstatus: String?): Boolean {
        return journalstatus?.let {
            it.equals("JOURNALFOERT", true) || it.equals("FERDIGSTILT", true)
        } ?: false
    }
}
