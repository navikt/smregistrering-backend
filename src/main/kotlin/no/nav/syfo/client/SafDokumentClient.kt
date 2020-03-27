package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.toByteArray
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): String? = retry("hent_dokument") {
        val httpResponse = httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV") {
            accept(ContentType.Application.Pdf)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("Nav-Callid", msgId)
            header("Nav-Consumer-Id", "smregistrering-backend")
        }.execute()
        if (httpResponse.status == InternalServerError) {
            log.error("Saf svarte med feilmelding ved henting av dokument for msgId {}, {}", msgId, fields(loggingMeta))
            throw IOException("Saf svarte med feilmelding ved henting av dokument for msgId $msgId")
        }
        when (NotFound) {
            httpResponse.status -> {
                log.error("Dokumentet finnes ikke for msgId {}, {}", msgId, fields(loggingMeta))
                null
            }
            else -> {
                log.info("Hentet OCR-dokument for msgId {}, {}", msgId, fields(loggingMeta))
                httpResponse.call.response.receive<String>()
            }
        }
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): ByteArray? {
        return try {
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, loggingMeta)
            dokument?.let {
                dokument.toByteArray()
            }
        } catch (ex: Exception) {
            log.warn("Klarte ikke å tolke ByteArray-dokument, {}: ${ex.message}", fields(loggingMeta))
            null
        }
    }
}
