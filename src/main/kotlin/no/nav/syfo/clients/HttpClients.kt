package no.nav.syfo.clients

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.exception.ServiceUnavailableException
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.syfosmregister.client.SyfosmregisterClient

class HttpClients(env: Environment) {
    private val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(HttpTimeout) {
            socketTimeoutMillis = 50_000L
            connectTimeoutMillis = 50_000L
            requestTimeoutMillis = 50_000L
        }
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.ALWAYS)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
    }

    private val httpClient = HttpClient(Apache, config)

    internal val azureAdV2Client =
        AzureAdV2Client(
            environment = env,
            httpClient = httpClient,
        )

    internal val oppgaveClient =
        OppgaveClient(env.oppgavebehandlingUrl, azureAdV2Client, httpClient, env.oppgaveScope)

    internal val safClient = SafDokumentClient(env, azureAdV2Client, httpClient)

    internal val smTssClient =
        SmtssClient(env.smtssApiUrl, azureAdV2Client, env.smtssApiScope, httpClient)

    internal val dokArkivClient =
        DokArkivClient(env.dokArkivUrl, azureAdV2Client, env.dokArkivScope, httpClient)

    internal val regelClient =
        RegelClient(env.regelEndpointURL, azureAdV2Client, env.syfosmpapirregelScope, httpClient)

    internal val syfoTilgangsKontrollClient =
        SyfoTilgangsKontrollClient(
            environment = env,
            azureAdV2Client = azureAdV2Client,
            httpClient = httpClient,
        )

    internal val msGraphClient =
        MSGraphClient(
            environment = env,
            azureAdV2Client = azureAdV2Client,
            httpClient = httpClient,
        )

    internal val pdlClient =
        PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class
                .java
                .getResource("/graphql/getPerson.graphql")!!
                .readText()
                .replace(Regex("[\n\t]"), ""),
        )

    internal val norskHelsenettClient =
        NorskHelsenettClient(
            env.norskHelsenettEndpointURL,
            azureAdV2Client,
            env.helsenettproxyScope,
            httpClient
        )

    internal val safJournalpostClient =
        SafJournalpostClient(
            httpClient,
            "${env.safV1Url}/graphql",
            SafJournalpostClient::class
                .java
                .getResource("/graphql/getJournalpostStatus.graphql")!!
                .readText()
                .replace(Regex("[\n\t]"), ""),
        )

    internal val syfoSmregisterClient =
        SyfosmregisterClient(env.syfoSmregisterEndpointURL, httpClient)
}
