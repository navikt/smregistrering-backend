package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.RuntimeException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.helpers.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.Oppgave

@KtorExperimentalAPI
class OppgaveClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun opprettOppgave(oppgave: Oppgave, msgId: String):
            Oppgave {

        log.info("Oppretter oppgave for msgId {}, journalpostId {}", msgId, oppgave.journalpostId)

        val httpResponse = httpClient.post<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = oppgave
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.Created -> {
                log.info("OppgaveClient opprettOppgave svarte 201 CREATED")
                httpResponse.call.response.receive()
            }
            else -> {
                log.error("OppgaveClient opprettOppgave kastet feil {} ved opprettelse av oppgave", httpResponse.status)
                throw RuntimeException("OppgaveClient opprettOppgave kastet feil $httpResponse.status")
            }
        }
    }

    suspend fun ferdigstillOppgave(ferdigstilloppgave: FerdigstillOppgave, msgId: String): Oppgave {

        log.info("Ferdigstiller oppgave med msgId {}, oppgaveId {} ", msgId, ferdigstilloppgave.id)

        val httpResponse = httpClient.patch<HttpStatement>(url + "/" + ferdigstilloppgave.id) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = ferdigstilloppgave
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive()
            }
            else -> {
                val msg = String.format("OppgaveClient ferdigstillOppgave kastet feil {} ved ferdigstilling av oppgave med id {} ", httpResponse.status, ferdigstilloppgave.id)
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String): Oppgave {

        log.info("Henter oppgave med oppgaveId {} msgId {}", oppgaveId, msgId)

        val httpResponse = httpClient.get<HttpStatement>("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive()
            }
            else -> {
                val msg = String.format("OppgaveClient hentOppgave kastet feil {} ved henting av oppgave med id {} ", httpResponse.status, oppgaveId)
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    suspend fun hentOppgaveVersjon(oppgaveId: Int, msgId: String): Int {
        return hentOppgave(oppgaveId, msgId).versjon
            ?: throw RuntimeException("Fant ikke versjon for oppgave $oppgaveId, msgId $msgId")
    }

    protected suspend fun oppdaterOppgave(oppgave: Oppgave, msgId: String): Oppgave {

        log.info("Oppdaterer oppgave med oppgaveId {} msgId {}", oppgave.id, msgId)

        log.info("Sender oppdatert oppgave til Oppgave: {}", StructuredArguments.fields(oppgave))

        val httpResponse = httpClient.put<HttpStatement>(url + "/" + oppgave.id) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = oppgave
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive()
            }
            else -> {
                val msg = "OppgaveClient oppdaterOppgave kastet feil ${httpResponse.status} ved oppdatering av oppgave med id ${oppgave.id}, response: ${httpResponse.call.response.receive<String>()}"
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    suspend fun sendOppgaveTilGosys(oppgaveId: Int, msgId: String, tildeltEnhetsnr: String, tilordnetRessurs: String): Oppgave {
        val oppgave = hentOppgave(oppgaveId, msgId)
        val oppdatertOppgave = oppgave.copy(
            behandlesAvApplikasjon = "FS22",
            tildeltEnhetsnr = tildeltEnhetsnr,
            tilordnetRessurs = tilordnetRessurs)
        return oppdaterOppgave(oppdatertOppgave, msgId)
    }
}

fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
    return setToWorkDay(ferdistilleDato)
}

fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
    when (ferdistilleDato.dayOfWeek) {
        DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
        DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
        else -> ferdistilleDato
    }
