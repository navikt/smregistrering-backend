package no.nav.syfo.saf

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException

class SafDokumentClient
constructor(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
) {
    private val url: String = environment.safV1Url
    private val scope: String = environment.safScope

    private suspend fun hentDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        accessToken: String,
        sykmeldingId: String,
    ): ByteArray {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(accessToken, scope)

        val httpResponse =
            httpClient.get("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
                accept(ContentType.Application.Pdf)
                header("Authorization", "Bearer $oboToken")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "smregistrering-backend")
            }

        log.info(
            "Saf returnerte: httpstatus {} for sykmeldingId {}",
            httpResponse.status,
            sykmeldingId
        )
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                log.info(
                    "Hentet papirsykmelding pdf for journalpostId {}, sykmeldingId {}",
                    journalpostId,
                    sykmeldingId
                )
                return httpResponse.body<ByteArray>()
            }
            HttpStatusCode.InternalServerError -> {
                log.error(
                    "Noe gikk galt ved sjekking av status eller tilgang for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.NotFound -> {
                log.error(
                    "Dokumentet finnes ikke for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw SafNotFoundException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Forbidden -> {
                log.warn(
                    "Bruker har ikke tilgang til for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw SafForbiddenException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.Unauthorized -> {
                log.warn(
                    "Bruker har ikke tilgang til for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw SafForbiddenException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.NotAcceptable -> {
                log.error(
                    "Not Acceptable for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            HttpStatusCode.BadRequest -> {
                log.error(
                    "Bad Requests for journalpostId {}, sykmeldingId {}, response {}",
                    journalpostId,
                    sykmeldingId,
                    httpResponse.body<String>()
                )
                throw RuntimeException("Saf returnerte: httpstatus ${httpResponse.status}")
            }
            else -> {
                log.error("Feil ved henting av dokument. Statuskode: ${httpResponse.status}")
                throw RuntimeException(
                    "En ukjent feil oppsto ved ved henting av dokument. Statuskode: ${httpResponse.status}"
                )
            }
        }
    }

    suspend fun hentDokument(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        accessToken: String,
        sykmeldingId: String,
    ): ByteArray? {
        log.info(
            "Henter dokument fra sykmeldingId {}, journalpostId {}, og dokumentInfoId {}",
            sykmeldingId,
            journalpostId,
            dokumentInfoId,
        )
        return hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, accessToken, sykmeldingId)
    }
}
