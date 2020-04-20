package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.encodeBase64
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

@InternalAPI
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
                accept(ContentType.Application.Any)
                header("Authorization", "Bearer $accessToken")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "smregistrering-backend")
            }.execute()

        when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Dokumentet finnes ikke for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.Forbidden -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Bruker har ikke tilgang til for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.NotAcceptable -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Not Acceptable for journalpostId {}", journalpostId)
                null
            }
            HttpStatusCode.BadRequest -> {
                log.error("Saf returnerte: httpstatus {}", httpResponse.status)
                log.error("Dårlig requests for journalpostId {}", journalpostId)
                null
            }
            else -> {
                log.info("Saf returnerte: httpstatus {}", httpResponse.status)
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
    ): String? {
        return try {
            log.info("Henter dokuemnt fra journalpostId {}, og dokumentInfoId {}", journalpostId, dokumentInfoId)
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken)
            dokument.let {
                dokument?.encodeBase64()
            }
        } catch (ex: Exception) {
            log.warn("Klarte ikke å tolke ByteArray-dokument ${ex.message}")
            null
        }
    }
}
