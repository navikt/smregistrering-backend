package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.sikkerlogg

class IstilgangskontrollClient(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val istilgangskontrollClientUrl: String = environment.istilgangskontrollClientUrl,
    private val scope: String = environment.istilgangskontrollScope,
    private val istilgangskontrollCache: Cache<Map<String, String>, Tilgang> =
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build(),
) {
    companion object {
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    suspend fun hasAccess(accessToken: String, personFnr: String): Tilgang {
        istilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            log.debug("Traff cache for istilgangskontroll")
            return it
        }
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)
        sikkerlogg.info("obo token for veileder: $oboToken")

        try {
            log.info("Sjekker tilgang for veileder på person")
            val httpResponse =
                httpClient.get("$istilgangskontrollClientUrl/api/tilgang/navident/person") {
                    accept(ContentType.Application.Json)
                    headers {
                        append("Authorization", "Bearer $oboToken")
                        append(NAV_PERSONIDENT_HEADER, personFnr)
                    }
                }
            return when (httpResponse.status) {
                HttpStatusCode.OK -> {
                    val tilgang = httpResponse.body<Tilgang>()
                    istilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
                    tilgang
                }
                else -> {
                    log.warn("istilgangskontroll svarte med ${httpResponse.status}")
                    Tilgang(
                        erGodkjent = false,
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("noe gikk galt ved oppslag mot istilgangskontroll")
            return Tilgang(
                erGodkjent = false,
            )
        }
    }

    suspend fun hasSuperuserAccess(accessToken: String, personFnr: String): Tilgang {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)

        try {
            log.info("Sjekker om veileder har utvidet tilgang til smreg")
            val httpResponse =
                httpClient.get(
                    "$istilgangskontrollClientUrl/api/tilgang/navident/person/papirsykmelding"
                ) {
                    accept(ContentType.Application.Json)
                    headers {
                        append("Authorization", "Bearer $oboToken")
                        append(NAV_PERSONIDENT_HEADER, personFnr)
                    }
                }
            return when (httpResponse.status) {
                HttpStatusCode.OK -> {
                    val tilgang = httpResponse.body<Tilgang>()
                    istilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
                    tilgang
                }
                else -> {
                    log.warn(
                        "istilgangskontroll svarte med ${httpResponse.status} på forespørsel om utvidet tilgang"
                    )
                    Tilgang(
                        erGodkjent = false,
                    )
                }
            }
        } catch (e: Exception) {
            log.warn(
                "noe gikk galt ved oppslag mot istilgangskontroll på forespørsel om utvidet tilgang"
            )
            return Tilgang(
                erGodkjent = false,
            )
        }
    }
}

data class Tilgang(
    val erGodkjent: Boolean,
)
