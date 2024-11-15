package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OpprettOppgave
import org.slf4j.LoggerFactory

class OppgaveClient(
    private val url: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val scope: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(OppgaveClient::class.java)
    }

    suspend fun opprettOppgave(oppgave: OpprettOppgave, msgId: String): Oppgave {
        log.info("Oppretter oppgave for msgId {}, journalpostId {}", msgId, oppgave.journalpostId)

        val httpResponse =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(oppgave)
            }
        return when (httpResponse.status) {
            HttpStatusCode.Created -> {
                log.info("OppgaveClient opprettOppgave svarte 201 CREATED")
                httpResponse.body<Oppgave>()
            }
            else -> {
                log.error(
                    "OppgaveClient opprettOppgave kastet feil ${httpResponse.status} ved opprettOppgave av oppgave, response: ${httpResponse.body<String>()}"
                )
                throw RuntimeException(
                    "OppgaveClient opprettOppgave kastet feil $httpResponse.status"
                )
            }
        }
    }

    suspend fun ferdigstillOppgave(ferdigstilloppgave: FerdigstillOppgave, msgId: String): Oppgave {
        log.info("Ferdigstiller oppgave med msgId {}, oppgaveId {} ", msgId, ferdigstilloppgave.id)

        val httpResponse =
            httpClient.patch(url + "/" + ferdigstilloppgave.id) {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(ferdigstilloppgave)
            }

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<Oppgave>()
            }
            else -> {
                val msg =
                    "OppgaveClient ferdigstillOppgave kastet feil ${httpResponse.status} " +
                        "ved ferdigstillOppgave av oppgave, response: ${httpResponse.body<String>()}" +
                        "oppgaveId: ${ferdigstilloppgave.id}" +
                        "tilordnetRessurs: ${ferdigstilloppgave.tilordnetRessurs}"
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String): Oppgave {
        log.info("Henter oppgave med oppgaveId {} msgId {}", oppgaveId, msgId)

        val httpResponse =
            httpClient.get("$url/$oppgaveId") {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
            }

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<Oppgave>()
            }
            else -> {
                val msg =
                    "OppgaveClient hentOppgave kastet feil ${httpResponse.status} ved hentOppgave av oppgave, response: ${httpResponse.body<String>()}"
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    internal suspend fun oppdaterOppgave(oppgave: Oppgave, msgId: String): Oppgave {
        log.info("Oppdaterer oppgave med oppgaveId {} msgId {}", oppgave.id, msgId)

        val httpResponse =
            httpClient.put(url + "/" + oppgave.id) {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(oppgave)
            }

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<Oppgave>()
            }
            HttpStatusCode.Conflict -> {
                val msg =
                    "OppgaveClient oppdaterOppgave kastet feil ${httpResponse.status} ved oppdatering av oppgave med id ${oppgave.id}, response: ${httpResponse.body<String>()}"
                log.warn(msg)
                throw RuntimeException(msg)
            }
            else -> {
                val msg =
                    "OppgaveClient oppdaterOppgave kastet feil ${httpResponse.status} ved oppdatering av oppgave med id ${oppgave.id}, response: ${httpResponse.body<String>()}"
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }
}
