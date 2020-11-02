package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.log
import no.nav.syfo.saf.exception.SafNotFoundException

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        accessToken: String
    ): ByteArray? {
        val httpResponse =
            httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
                accept(ContentType.Application.Pdf)
                header("Authorization", "Bearer $accessToken")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "smregistrering-backend")
            }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Dokumentet finnes ikke for journalpostId {}", journalpostId)
                throw SafNotFoundException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}", journalpostId)
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.NotAcceptable -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Not Acceptable for journalpostId {}", journalpostId)
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Dårlig requests for journalpostId {}", journalpostId)
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            else -> {
                log.info("Saf returnerte: httpstatus {}", httpResponse.status)
                log.info("Hentet papirsykmelding pdf for journalpostId {}", journalpostId)
                httpResponse.call.response.receive<ByteArray>()
            }
        }
    }

    suspend fun hentDokument(
        journalpostId: String,
        dokumentInfoId:
        String,
        msgId: String,
        accessToken: String,
        oppgaveId: Int
    ): ByteArray? {
        log.info(
            "Henter dokument fra oppgaveId {}, journalpostId {}, og dokumentInfoId {}",
            oppgaveId,
            journalpostId,
            dokumentInfoId
        )
        return hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken)
    }
}
