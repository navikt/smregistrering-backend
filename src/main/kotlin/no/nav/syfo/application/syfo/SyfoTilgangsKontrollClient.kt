package no.nav.syfo.application.syfo

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

class SyfoTilgangsKontrollClient(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? =
        retry("tilgang_til_person_via_azure") {
            val httpResponse = httpClient.get<HttpStatement>("$url/api/tilgang/navident/bruker/$personFnr") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
        }.execute()
            when (httpResponse.status) {
                HttpStatusCode.InternalServerError -> {
                    log.error("syfo-tilgangskontroll svarte med InternalServerError")
                    Tilgang(
                        harTilgang = false,
                        begrunnelse = "syfo-tilgangskontroll svarte med InternalServerError"
                    )
                }

                HttpStatusCode.BadRequest -> {
                    log.error("syfo-tilgangskontroll svarer med BadRequest")
                    return@retry Tilgang(
                        harTilgang = false,
                        begrunnelse = "syfo-tilgangskontroll svarer med BadRequest"
                    )
                }

                HttpStatusCode.NotFound -> {
                    log.warn("syfo-tilgangskontroll svarer med NotFound")
                    return@retry Tilgang(
                        harTilgang = false,
                        begrunnelse = "syfo-tilgangskontroll svarer med NotFound"
                    )
                }

                HttpStatusCode.Unauthorized -> {
                    log.warn("syfo-tilgangskontroll svarer med Unauthorized")
                    return@retry Tilgang(
                        harTilgang = false,
                        begrunnelse = "syfo-tilgangskontroll svarer med Unauthorized"
                    )
                }

                else -> {
                    log.info("syfo-tilgangskontroll svarer med httpResponse status kode: {}", httpResponse.status.value)
                    log.info("Sjekker tilgang for veileder på person")
                    httpResponse.call.response.receive<Tilgang>()
                }
            }
    }

    suspend fun hentVeilderIdentViaAzure(accessToken: String): Veilder? =
        retry("tilgang_til_person_via_azure") {
            val httpResponse = httpClient.get<HttpStatement>("$url/api/veilederinfo/ident") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $accessToken")
                }
            }.execute()
            when (httpResponse.status) {
                HttpStatusCode.InternalServerError -> {
                    log.error("syfo-tilgangskontroll svarte med InternalServerError")
                    return@retry null
                }

                HttpStatusCode.BadRequest -> {
                    log.error("syfo-tilgangskontroll svarer med BadRequest")
                    return@retry null                }

                HttpStatusCode.NotFound -> {
                    log.warn("syfo-tilgangskontroll svarer med NotFound")
                    return@retry null                }

                HttpStatusCode.Unauthorized -> {
                    log.warn("syfo-tilgangskontroll svarer med Unauthorized")
                    return@retry null
                }

                else -> {
                    log.info("syfo-tilgangskontroll svarer med httpResponse status kode: {}", httpResponse.status.value)
                    log.info("Sjekker tilgang for veileder på person")
                    httpResponse.call.response.receive<Veilder>()
                }
            }
        }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)

data class Veilder(
    val ident: String
)
