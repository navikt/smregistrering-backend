package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException

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
        sykmeldingId: String
    ): ByteArray {

        val oboToken = azureAdV2Client.getOnBehalfOfToken(accessToken, scope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente accessToken for SAF")

        try {
            val httpResponse =
                httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
                    accept(ContentType.Application.Pdf)
                    header("Authorization", "Bearer $oboToken")
                    header("Nav-Callid", msgId)
                    header("Nav-Consumer-Id", "smregistrering-backend")
                }.execute()

            log.info("Saf returnerte: httpstatus {} for sykmeldingId {}", httpResponse.status, sykmeldingId)
            log.info("Hentet papirsykmelding pdf for journalpostId {}, sykmeldingId {}", journalpostId, sykmeldingId)
            return httpResponse.call.response.receive<ByteArray>()
        } catch (e: Exception) {
            if (e is ResponseException) {
                when (e.response.status) {
                    HttpStatusCode.InternalServerError -> {
                        log.error("Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw RuntimeException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    HttpStatusCode.NotFound -> {
                        log.error("Dokumentet finnes ikke for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw SafNotFoundException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    HttpStatusCode.Forbidden -> {
                        log.warn("Bruker har ikke tilgang til for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw SafForbiddenException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    HttpStatusCode.Unauthorized -> {
                        log.warn("Bruker har ikke tilgang til for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw SafForbiddenException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    HttpStatusCode.NotAcceptable -> {
                        log.error("Not Acceptable for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw RuntimeException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    HttpStatusCode.BadRequest -> {
                        log.error("Bad Requests for journalpostId {}, sykmeldingId {}, response {}", journalpostId, sykmeldingId, e.response.call.response.receive())
                        throw RuntimeException("Saf returnerte: httpstatus ${e.response.status}")
                    }
                    else -> {
                        log.error("Feil ved henting av dokument. Statuskode: ${e.response.status}")
                        throw RuntimeException("En ukjent feil oppsto ved ved henting av dokument. Statuskode: ${e.response.status}")
                    }
                }
            } else {
                log.error("Noe gikk galt ved henting av dokument for journalpostId {}, sykmeldingId {}", journalpostId, sykmeldingId)
                throw RuntimeException("En ukjent feil oppsto ved ved henting av dokument for journalpostId $journalpostId, sykmeldingId $sykmeldingId")
            }
        }
    }

    suspend fun hentDokument(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        accessToken: String,
        sykmeldingId: String
    ): ByteArray? {
        log.info(
            "Henter dokument fra sykmeldingId {}, journalpostId {}, og dokumentInfoId {}",
            sykmeldingId,
            journalpostId,
            dokumentInfoId
        )
        return hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken, sykmeldingId)
    }
}
