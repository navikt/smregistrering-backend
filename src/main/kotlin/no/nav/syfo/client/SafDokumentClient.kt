package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.toByteArray
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.objectMapper

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
    ): String? = retry("hent_dokument") {
        val httpResponse =
            httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
                accept(ContentType.Application.Pdf)
                header("Authorization", "Bearer $accessToken")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "smregistrering-backend")
            }.execute()

        when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Dokumentet finnes ikke for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.Forbidden -> {
                log.warn("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.warn("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.BadRequest -> {
                log.warn("Dårlig requests for journalpostId {}", journalpostId)
                null
            }
            else -> {
                log.info("Hentet papirsykmelding pdf for journalpostId {}", journalpostId)
                httpResponse.call.response.receive<String>()
            }
        }
    }

    suspend fun hentDokument(
        journalpostId: String,
        dokumentInfoId:
        String,
        msgId: String,
        accessToken: String
    ): ByteArray? {
        return try {
            log.info("Henter dokuemnt fra journalpostId {}, og dokumentInfoId {}", journalpostId, dokumentInfoId)
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken)
            log.info("Dokument fra saf: ${objectMapper.writeValueAsString(dokument)}")
            dokument?.let {
                dokument.toByteArray()
            }
        } catch (ex: Exception) {
            log.warn("Klarte ikke å tolke ByteArray-dokument ${ex.message}")
            null
        }
    }
}
