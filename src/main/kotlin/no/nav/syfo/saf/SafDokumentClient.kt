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
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.saf.exception.SafNotFoundException

@KtorExperimentalAPI
class SafDokumentClient constructor(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient
) {
    private val url: String = environment.hentDokumentUrl
    private val scope: String = environment.safScope

    private suspend fun hentDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        accessToken: String,
        oppgaveId: Int
    ): ByteArray? {

        val oboToken = azureAdV2Client.getOnBehalfOfToken(accessToken, scope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente accessToken for SAF")

        val httpResponse =
            httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
                accept(ContentType.Application.Pdf)
                header("Authorization", "Bearer $oboToken")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "smregistrering-backend")
            }.execute()

        log.info("Saf returnerte: httpstatus {} for oppgaveId {}", httpResponse.status, oppgaveId)

        return when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Dokumentet finnes ikke for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw SafNotFoundException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Bruker har ikke tilgang til for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Bruker har ikke tilgang til for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.NotAcceptable -> {
                log.error("Not Acceptable for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Bad Requests for journalpostId {}, oppgaveId {}, response {}", journalpostId, oppgaveId, httpResponse.call.response.receive())
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            else -> {
                log.info("Hentet papirsykmelding pdf for journalpostId {}, oppgaveId {}", journalpostId, oppgaveId)
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
        return hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken, oppgaveId)
    }
}
