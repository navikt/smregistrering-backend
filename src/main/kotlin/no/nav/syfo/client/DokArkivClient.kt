package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.padHpr
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * REST-klient for å utføre operasjoner mot DokArkiv
 */
class DokArkivClient(
    private val url: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val scope: String,
    private val httpClient: HttpClient
) {

    suspend fun oppdaterOgFerdigstillJournalpost(
        journalpostId: String,
        dokumentInfoId: String? = null,
        pasientFnr: String,
        sykmeldingId: String,
        sykmelder: Sykmelder,
        loggingMeta: LoggingMeta,
        navEnhet: String,
        avvist: Boolean,
        receivedSykmelding: ReceivedSykmelding?
    ): String? {
        oppdaterJournalpost(
            journalpostId = journalpostId, dokumentInfoId = dokumentInfoId, pasientFnr = pasientFnr,
            sykmelder = sykmelder, avvist = avvist, msgId = sykmeldingId, receivedSykmelding = receivedSykmelding,
            loggingMeta = loggingMeta
        )
        return ferdigstillJournalpost(
            journalpostId = journalpostId, msgId = sykmeldingId, loggingMeta = loggingMeta,
            navEnhet = navEnhet
        )
    }

    private suspend fun oppdaterJournalpost(
        journalpostId: String,
        dokumentInfoId: String?,
        pasientFnr: String,
        sykmelder: Sykmelder,
        avvist: Boolean,
        msgId: String,
        receivedSykmelding: ReceivedSykmelding?,
        loggingMeta: LoggingMeta
    ) {
        val httpResponse = httpClient.put("$url/$journalpostId") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val token = azureAdV2Client.getAccessToken(scope)
            header("Authorization", "Bearer $token")
            header("Nav-Callid", msgId)

            setBody(
                OppdaterJournalpost(
                    avsenderMottaker = AvsenderMottaker(
                        id = padHpr(sykmelder.hprNummer),
                        navn = finnNavn(sykmelder)
                    ),
                    bruker = Bruker(id = pasientFnr),
                    sak = Sak(),
                    tittel = getTittel(avvist, receivedSykmelding),
                    dokumenter = if (dokumentInfoId != null) {
                        listOf(DokumentInfo(dokumentInfoId = dokumentInfoId, tittel = getTittel(avvist, receivedSykmelding)))
                    } else {
                        null
                    }
                )
            )
        }
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                log.info("Oppdatering av journalpost ok for journalpostid {}, msgId {}, http status {} , {}", journalpostId, msgId, httpResponse.status.value, fields(loggingMeta))
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for $journalpostId msgid $msgId")
            }
            HttpStatusCode.NotFound -> {
                log.error("Oppdatering: Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Oppdatering: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.BadRequest.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk BadRequest ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.Unauthorized.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 401 ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.Forbidden.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 403 ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.error("Feil ved oppdatering av journalpostid {}, msgId {}, {}. Statuskode: ${httpResponse.status}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("En ukjent feil oppsto ved oppdatering av journalpostid $journalpostId. Statuskode: ${httpResponse.status}")
            }
        }
    }

    suspend fun ferdigstillJournalpost(
        journalpostId: String,
        msgId: String,
        loggingMeta: LoggingMeta,
        navEnhet: String
    ): String {
        val httpResponse = httpClient.patch("$url/$journalpostId/ferdigstill") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val token = azureAdV2Client.getAccessToken(scope)
            header("Authorization", "Bearer $token")
            header("Nav-Callid", msgId)
            setBody(FerdigstillJournal(navEnhet))
        }
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                log.info("ferdigstilling av journalpost ok for journalpostid {}, msgId {}, http status {} , {}", journalpostId, msgId, httpResponse.status.value, fields(loggingMeta))
                return httpResponse.body<String>()
            }
            HttpStatusCode.InternalServerError -> {
                log.error("Dokakriv svarte med feilmelding ved ferdigstilling av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Saf svarte med feilmelding ved ferdigstilling av journalpost for $journalpostId msgid $msgId")
            }
            HttpStatusCode.NotFound -> {
                log.error("Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Oppdatering: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.BadRequest.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk BadRequest ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Unauthorized.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk Unauthorized ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Forbidden.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk Forbidden ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.error("Feil ved ferdigstilling av journalpostid {}, msgId {}, {}. Statuskode: ${httpResponse.status}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("En ukjent feil oppsto ved ferdigstilling av journalpostid $journalpostId. Statuskode: ${httpResponse.status}")
            }
        }
    }

    fun finnNavn(sykmelder: Sykmelder): String {
        return "${sykmelder.fornavn} ${sykmelder.etternavn}"
    }

    fun getTittel(avvist: Boolean, receivedSykmelding: ReceivedSykmelding?): String {
        return if (avvist) {
            if (receivedSykmelding != null) {
                "Avvist papirsykmelding ${getFomTomTekst(receivedSykmelding)}"
            } else {
                "Avvist papirsykmelding"
            }
        } else {
            if (receivedSykmelding != null) {
                "Papirsykmelding ${getFomTomTekst(receivedSykmelding)}"
            } else {
                "Papirsykmelding"
            }
        }
    }

    private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
        "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

    fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }
    fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
        sortedBy { it.tom }

    fun formaterDato(dato: LocalDate): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return dato.format(formatter)
    }

    data class FerdigstillJournal(
        val journalfoerendeEnhet: String
    )

    data class OppdaterJournalpost(
        val tema: String = "SYM",
        val avsenderMottaker: AvsenderMottaker,
        val bruker: Bruker,
        val sak: Sak,
        val tittel: String,
        val dokumenter: List<DokumentInfo>?
    )

    data class AvsenderMottaker(
        val id: String?,
        val idType: String = "HPRNR",
        val navn: String
    )

    data class Bruker(
        val id: String,
        val idType: String = "FNR"
    )

    data class Sak(
        val sakstype: String = "GENERELL_SAK"
    )

    data class DokumentInfo(
        val dokumentInfoId: String,
        val tittel: String?
    )
}
