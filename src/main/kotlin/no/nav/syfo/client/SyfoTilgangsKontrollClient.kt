package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import java.util.concurrent.TimeUnit

class SyfoTilgangsKontrollClient(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val syfoTilgangsKontrollClientUrl: String = environment.syfoTilgangsKontrollClientUrl,
    private val scope: String = environment.syfoTilgangsKontrollScope,
    private val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build()
) {
    companion object {
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    suspend fun hasAccess(accessToken: String, personFnr: String): Tilgang? {
        syfoTilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            log.debug("Traff cache for syfotilgangskontroll")
            return it
        }
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente nytt accessToken for veileder ved tilgangssjekk")

        try {
            log.info("Sjekker tilgang for veileder på person")
            val tilgang = httpClient.get<Tilgang>("$syfoTilgangsKontrollClientUrl/api/tilgang/navident/person") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $oboToken")
                    append(NAV_PERSONIDENT_HEADER, personFnr)
                }
            }
            syfoTilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
            return tilgang
        } catch (e: Exception) {
            val feilmelding = if (e is ResponseException) {
                "syfo-tilgangskontroll svarte med ${e.response.status}"
            } else {
                "noe gikk galt ved oppslag mot syfo-tilgangskontroll"
            }
            return Tilgang(
                harTilgang = false,
                begrunnelse = feilmelding
            )
        }
    }

    suspend fun hasSuperuserAccess(accessToken: String, personFnr: String): Tilgang? {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente nytt accessToken for veileder ved tilgangssjekk")

        try {
            log.info("Sjekker om veileder har utvidet tilgang til smreg")
            val tilgang = httpClient.get<Tilgang>("$syfoTilgangsKontrollClientUrl/api/tilgang/navident/person/papirsykmelding") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $oboToken")
                    append(NAV_PERSONIDENT_HEADER, personFnr)
                }
            }
            return tilgang
        } catch (e: Exception) {
            val feilmelding = if (e is ResponseException) {
                "syfo-tilgangskontroll svarte med ${e.response.status} på forespørsel om utvidet tilgang"
            } else {
                "noe gikk galt ved oppslag mot syfo-tilgangskontroll"
            }
            return Tilgang(
                harTilgang = false,
                begrunnelse = feilmelding
            )
        }
    }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
