package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.util.padHpr
import java.io.IOException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun finnBehandler(hprNummer: String, callId: String): Behandler? {
        log.info("Henter behandler fra syfohelsenettproxy for callId {}", callId)
        val accessToken = azureAdV2Client.getAccessToken(resourceId)
        try {
            return httpClient.get("$endpointUrl/api/v2/behandlerMedHprNummer") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Nav-CallId", callId)
                    append("hprNummer", padHpr(hprNummer)!!)
                }
            }.body<Behandler>().also { log.info("Hentet behandler for callId {}", callId) }
        } catch (e: Exception) {
            if (e is ResponseException) {
                when (e.response.status) {
                    InternalServerError -> {
                        log.error("Syfohelsenettproxy svarte med feilmelding for callId {}", callId)
                        throw IOException("Syfohelsenettproxy svarte med feilmelding for $callId")
                    }
                    NotFound -> {
                        log.warn("Fant ikke behandler for HprNummer $hprNummer for callId $callId")
                        throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")
                    }
                    Unauthorized -> {
                        log.warn("Norsk helsenett returnerte Unauthorized for henting av behandler: $hprNummer")
                        throw UnauthorizedException("Norsk helsenett returnerte Unauthorized ved oppslag av HPR-nummer $hprNummer")
                    }
                    else -> {
                        log.error("Feil ved henting av behandler. Statuskode: ${e.response.status}")
                        throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler. Statuskode: ${e.response.status}")
                    }
                }
            } else {
                log.error("Noe gikk galt ved henting av behandler fra syfohelsenettproxy $callId")
                throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler $callId")
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?
)
