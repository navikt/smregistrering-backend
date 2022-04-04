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
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.SyfosmregisterClient
import no.nav.syfo.clients.exception.ServiceUnavailableException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.SafJournalpostClient
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

class HttpClients(env: Environment, vaultSecrets: VaultSecrets) {
    private val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        engine {
            socketTimeout = 40_000
            connectTimeout = 40_000
            connectionRequestTimeout = 40_000
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.ALWAYS)
            }
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }

    private val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    private val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    private val httpClient = HttpClient(Apache, config)

    internal val azureAdV2Client = AzureAdV2Client(
        environment = env,
        httpClient = httpClientWithProxy
    )

    private val oidcClient =
        StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenUrl)

    internal val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    internal val safClient = SafDokumentClient(env, azureAdV2Client, httpClient)

    internal val sarClient = SarClient(env.kuhrSarApiUrl, azureAdV2Client, env.kuhrSarApiScope, httpClient)

    internal val dokArkivClient = DokArkivClient(env.dokArkivUrl, azureAdV2Client, env.dokArkivScope, httpClient)

    internal val regelClient =
        RegelClient(env.regelEndpointURL, azureAdV2Client, env.syfosmpapirregelScope, httpClient)

    internal val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )

    internal val msGraphClient = MSGraphClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )

    internal val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )

    internal val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, azureAdV2Client, env.helsenettproxyScope, httpClient)

    internal val safJournalpostClient = SafJournalpostClient(
        httpClient,
        env.safJournalpostGraphqlPath,
        SafJournalpostClient::class.java.getResource("/graphql/getJournalpostStatus.graphql").readText().replace(Regex("[\n\t]"), "")
    )

    internal val syfoSmregisterClient = SyfosmregisterClient(env.syfoSmregisterEndpointURL, httpClient)
}
