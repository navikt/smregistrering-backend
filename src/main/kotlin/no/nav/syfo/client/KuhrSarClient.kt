package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import kotlin.math.max
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.log
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.SamhandlerPeriode
import no.nav.syfo.model.SamhandlerPraksisMatch
import no.nav.syfo.model.SamhandlerPraksisType
import no.nav.syfo.util.LoggingMeta
import org.apache.commons.text.similarity.LevenshteinDistance

@KtorExperimentalAPI
class SarClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String): List<Samhandler> {
        return httpClient.get("$endpointUrl/rest/sar/samh") {
            accept(ContentType.Application.Json)
            parameter("ident", ident)
        }
    }
}

fun calculatePercentageStringMatch(str1: String?, str2: String): Double {
    val maxDistance = max(str1?.length!!, str2.length).toDouble()
    val distance = LevenshteinDistance().apply(str2, str1).toDouble()
    return (maxDistance - distance) / maxDistance
}

fun List<SamhandlerPeriode>.formaterPerioder() = joinToString(",", "periode(", ") ") { periode ->
    "${periode.gyldig_fra} -> ${periode.gyldig_til}"
}

fun List<Samhandler>.formaterPraksis() = flatMap { it.samh_praksis }
    .joinToString(",", "praksis(", ") ") { praksis ->
        "${praksis.navn}: ${praksis.samh_praksis_status_kode} ${praksis.samh_praksis_periode.formaterPerioder()}"
    }

fun findBestSamhandlerPraksis(
    samhandlere: List<Samhandler>,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }

    if (aktiveSamhandlere.isEmpty()) {
        log.info(
            "Fant ingen aktive samhandlere. {}  Meta: {}, {} ",
            keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
            keyValue("antall praksiser", samhandlere.size),
            fields(loggingMeta)
        )
    }

    val aktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { !it.navn.isNullOrEmpty() }

    if (aktiveSamhandlereMedNavn.isNullOrEmpty() && !aktiveSamhandlere.isNullOrEmpty()) {
        val samhandlerFALEOrFALO = aktiveSamhandlere.find {
            it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLEGE.kodeVerdi ||
                    it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLONNET.kodeVerdi
        }
        if (samhandlerFALEOrFALO != null) {
            return SamhandlerPraksisMatch(samhandlerFALEOrFALO, 999.0)
        }
        if (samhandlere.firstOrNull()?.samh_praksis != null &&
            samhandlere.firstOrNull()?.samh_praksis?.firstOrNull() != null
        ) {
            val firstSamhnalderPraksis = samhandlere.firstOrNull()?.samh_praksis?.firstOrNull()
            if (firstSamhnalderPraksis != null && firstSamhnalderPraksis.tss_ident.isNotEmpty()) {
                log.info(
                    "Siste utvei med tss matching ble samhandler praksis: " +
                            "Orgnumer: ${firstSamhnalderPraksis.org_id} " +
                            "Navn: ${firstSamhnalderPraksis.navn} " +
                            "Tssid: ${firstSamhnalderPraksis.tss_ident} " +
                            "Adresselinje1: ${firstSamhnalderPraksis.arbeids_adresse_linje_1} " +
                            "Samhandler praksis type: ${firstSamhnalderPraksis.samh_praksis_type_kode} " +
                            "Samhandlers hpr nummer: ${samhandlere.firstOrNull()?.samh_ident?.find { it.ident_type_kode == "HPR" }?.ident} " +
                            "{}", fields(loggingMeta)
                )
                return SamhandlerPraksisMatch(firstSamhnalderPraksis, 999.0)
            }
        }
    }

    return null
}

fun samhandlerMatchingPaaOrganisjonsNavn(samhandlere: List<Samhandler>, orgName: String): SamhandlerPraksisMatch? {
    val inaktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { samhandlerPraksis -> samhandlerPraksis.samh_praksis_status_kode == "inaktiv" }
        .filter { samhandlerPraksis -> !samhandlerPraksis.navn.isNullOrEmpty() }
    return if (!inaktiveSamhandlereMedNavn.isNullOrEmpty()) {
        inaktiveSamhandlereMedNavn
            .map { samhandlerPraksis ->
                SamhandlerPraksisMatch(
                    samhandlerPraksis,
                    calculatePercentageStringMatch(samhandlerPraksis.navn?.toLowerCase(), orgName.toLowerCase()) * 100
                )
            }.maxBy { it.percentageMatch }
    } else {
        null
    }
}
