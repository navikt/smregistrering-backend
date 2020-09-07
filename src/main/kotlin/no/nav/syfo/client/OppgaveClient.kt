package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.lang.RuntimeException
import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.syfo.helpers.log
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.FerdigStillOppgave
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse

@KtorExperimentalAPI
class OppgaveClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun opprettOppgave(opprettOppgave: OpprettOppgave, msgId: String):
            OpprettOppgaveResponse = retry("create_oppgave") {

        log.info("Oppretter oppgave {} ", opprettOppgave)

        val httpResponse = httpClient.post<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = opprettOppgave
        }.execute()

        when (httpResponse.status) {
            HttpStatusCode.Created -> {
                log.info("OppgaveClient opprettOppgave svarte 201 CREATED")
                httpResponse.call.response.receive<OpprettOppgaveResponse>()
            }
            else -> {
                log.error("OppgaveClient opprettOppgave kastet feil {} ved opprettelse av oppgave", httpResponse.status)
                throw RuntimeException("OppgaveClient opprettOppgave kastet feil $httpResponse.status")
            }
        }
    }

    suspend fun ferdigStillOppgave(ferdigstilloppgave: FerdigStillOppgave, msgId: String):
            OpprettOppgaveResponse = retry("ferdigstill_oppgave") {

        log.info("Ferdigstiller oppgave {} ", ferdigstilloppgave)

        val httpResponse = httpClient.patch<HttpStatement>(url + "/" + ferdigstilloppgave.id) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = ferdigstilloppgave
        }.execute()

        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive<OpprettOppgaveResponse>()
            }
            else -> {
                log.error("OppgaveClient ferdigStillOppgave kastet feil {} ved ferdigstilling av oppgave", httpResponse.status)
                throw RuntimeException("OppgaveClient ferdigStillOppgave kastet feil $httpResponse.status")
            }
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String):
            OpprettOppgaveResponse = retry("hent_oppgave") {

        log.info("Henter oppgave med oppgaveId {} msgId {}", oppgaveId, msgId)

        val httpResponse = httpClient.get<HttpStatement>("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
        }.execute()

        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive<OpprettOppgaveResponse>()
            }
            else -> {
                log.error("OppgaveClient hentOppgave kastet feil {} ved henting av oppgave", httpResponse.status)
                throw RuntimeException("OppgaveClient hentOppgave kastet feil $httpResponse.status")
            }
        }
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
