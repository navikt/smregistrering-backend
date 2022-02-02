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
import no.nav.syfo.helpers.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OpprettOppgave
import java.time.DayOfWeek
import java.time.LocalDate

class OppgaveClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun opprettOppgave(oppgave: OpprettOppgave, msgId: String):
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
                log.error("OppgaveClient opprettOppgave kastet feil ${httpResponse.status} ved opprettOppgave av oppgave, response: ${httpResponse.call.response.receive<String>()}")
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
                val msg = "OppgaveClient ferdigstillOppgave kastet feil ${httpResponse.status} ved ferdigstillOppgave av oppgave, response: ${httpResponse.call.response.receive<String>()}"
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
                val msg = "OppgaveClient hentOppgave kastet feil ${httpResponse.status} ved hentOppgave av oppgave, response: ${httpResponse.call.response.receive<String>()}"
                log.error(msg)
                throw RuntimeException(msg)
            }
        }
    }

    private suspend fun oppdaterOppgave(oppgave: Oppgave, msgId: String): Oppgave {

        log.info("Oppdaterer oppgave med oppgaveId {} msgId {}", oppgave.id, msgId)

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

    suspend fun sendOppgaveTilGosys(oppgaveId: Int, msgId: String, tilordnetRessurs: String): Oppgave {
        val oppgave = hentOppgave(oppgaveId, msgId)
        val oppdatertOppgave = oppgave.copy(
            behandlesAvApplikasjon = "FS22",
            tilordnetRessurs = tilordnetRessurs,
            mappeId = null
        )
        return oppdaterOppgave(oppdatertOppgave, msgId)
    }

    suspend fun patchManuellOppgave(oppgaveId: Int, msgId: String): Oppgave {
        val oppgave = hentOppgave(oppgaveId, msgId)
        if (oppgave.status == "FERDIGSTILT") {
            log.warn("Oppgave med id $oppgaveId er allerede ferdigstilt. Oppretter ny oppgave for msgId $msgId")
            return opprettOppgave(
                OpprettOppgave(
                    aktoerId = oppgave.aktoerId,
                    opprettetAvEnhetsnr = "9999",
                    behandlesAvApplikasjon = "SMR",
                    beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                    tema = "SYM",
                    oppgavetype = "JFR",
                    aktivDato = LocalDate.now(),
                    fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                        LocalDate.now().plusDays(4)
                    ),
                    prioritet = "HOY",
                    journalpostId = oppgave.journalpostId
                ),
                msgId
            )
        } else {
            val patch = oppgave.copy(
                behandlesAvApplikasjon = "SMR",
                beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                mappeId = null,
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                    LocalDate.now().plusDays(4)
                ),
                prioritet = "HOY"
            )
            return oppdaterOppgave(patch, msgId)
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
