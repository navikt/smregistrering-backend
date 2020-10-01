package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import kotlin.math.max
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.SamhandlerPeriode
import no.nav.syfo.model.SamhandlerPraksis
import no.nav.syfo.model.SamhandlerPraksisMatch
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
    return getAktivOrInaktivSamhandlerPraksis(samhandlere).let {
        when (it) {
            null -> null
            else -> SamhandlerPraksisMatch(it, 100.0)
        }
    }
}

private fun getAktivOrInaktivSamhandlerPraksis(samhandlere: List<Samhandler>): SamhandlerPraksis? {
    val activSamhandlerPraksis = samhandlere.flatMap { it.samh_praksis }.groupBy { it.samh_praksis_status_kode }
    return activSamhandlerPraksis["aktiv"]?.firstOrNull() ?: activSamhandlerPraksis["inaktiv"]?.firstOrNull()
}

fun isInactiv(it: SamhandlerPraksis): Boolean {
    return it.samh_praksis_status_kode == "inaktiv"
}

private fun isAktiv(it: SamhandlerPraksis): Boolean {
    return it.samh_praksis_status_kode == "aktiv"
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
