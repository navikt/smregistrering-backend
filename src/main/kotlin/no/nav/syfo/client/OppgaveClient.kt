package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
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

        val opprettOppgaveResponse = httpClient.post<OpprettOppgaveResponse>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = opprettOppgave
        }

        log.info("Opprettet oppgave {} ", opprettOppgaveResponse)

        opprettOppgaveResponse
    }

    suspend fun ferdigStillOppgave(ferdigstilloppgave: FerdigStillOppgave, msgId: String):
            OpprettOppgaveResponse = retry("ferdigstill_oppgave") {
        httpClient.patch<OpprettOppgaveResponse>(url + "/" + ferdigstilloppgave.id) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = ferdigstilloppgave
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String):
            OpprettOppgaveResponse = retry("hent_oppgave") {
        httpClient.get<OpprettOppgaveResponse>("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
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
