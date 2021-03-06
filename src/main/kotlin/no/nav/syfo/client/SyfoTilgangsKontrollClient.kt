package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log

class SyfoTilgangsKontrollClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scopeSyfotilgangskontroll: String,
    private val httpClient: HttpClient,
    private val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang>,
    private val veilederCache: Cache<String, Veileder>
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? {
        syfoTilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            log.debug("Traff cache for syfotilgangskontroll")
            return it
        }
        val oboToken = accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(accessToken = accessToken, scope = scopeSyfotilgangskontroll)
        val httpResponse = httpClient.get<HttpStatement>("$url/api/tilgang/navident/bruker/$personFnr") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $oboToken")
            }
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.warn("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarte med InternalServerError")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarte med InternalServerError"
                )
            }
            HttpStatusCode.BadRequest -> {
                log.warn("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarer med BadRequest")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med BadRequest"
                )
            }
            HttpStatusCode.NotFound -> {
                log.warn("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarer med NotFound")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med NotFound"
                )
            }
            HttpStatusCode.Unauthorized -> {
                log.warn("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarer med Unauthorized")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med Unauthorized"
                )
            }
            HttpStatusCode.Forbidden -> {
                log.warn("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarer med Forbidden")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med Forbidden"
                )
            }
            HttpStatusCode.OK -> {
                log.debug("syfo-tilgangskontroll sjekkVeiledersTilgangTilPersonViaAzure svarer med ok")
                log.info("Sjekker tilgang for veileder på person")
                val tilgang = httpResponse.call.response.receive<Tilgang>()
                syfoTilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
                return tilgang
            }
        }
        log.warn("Mottok ukjent responskode fra syfotilgangskontroll: ${httpResponse.status}")
        throw IllegalStateException("Mottok ukjent responskode fra syfotilgangskontroll: ${httpResponse.status}")
    }

    suspend fun hentVeilderIdentViaAzure(accessToken: String): Veileder? {
        veilederCache.getIfPresent(accessToken)?.let {
            log.debug("Traff cache for syfotilgangskontroll")
            return it
        }
        val oboToken = accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(accessToken = accessToken, scope = scopeSyfotilgangskontroll)
        val httpResponse = httpClient.get<HttpStatement>("$url/api/veilederinfo/ident") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $oboToken")
            }
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("syfo-tilgangskontroll hentVeilderIdentViaAzure svarte med InternalServerError")
                return null
            }
            HttpStatusCode.BadRequest -> {
                log.error("syfo-tilgangskontroll hentVeilderIdentViaAzure svarer med BadRequest")
                return null
            }
            HttpStatusCode.NotFound -> {
                log.warn("syfo-tilgangskontroll hentVeilderIdentViaAzure svarer med NotFound")
                return null
            }
            HttpStatusCode.Unauthorized -> {
                log.warn("syfo-tilgangskontroll hentVeilderIdentViaAzure svarer med Unauthorized")
                return null
            }
            HttpStatusCode.OK -> {
                log.debug("syfo-tilgangskontroll hentVeilderIdentViaAzure svarer med ok")
                log.info("Henter veilederident")
                val veileder = httpResponse.call.response.receive<Veileder>()
                veilederCache.put(accessToken, veileder)
                return veileder
            }
        }
        log.error("Mottok ukjent responskode fra syfotilgangskontroll, hentVeilderIdentViaAzure: ${httpResponse.status}")
        throw IllegalStateException("Mottok ukjent responskode fra syfotilgangskontroll, hentVeilderIdentViaAzure: ${httpResponse.status}")
    }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)

data class Veileder(
    val veilederIdent: String
)
