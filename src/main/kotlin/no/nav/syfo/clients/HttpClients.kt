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
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import java.net.ProxySelector
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
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.sykmelder.service.SykmelderService
import org.apache.http.impl.conn.SystemDefaultRoutePlanner

@KtorExperimentalAPI
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
        expectSuccess = false
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

    private val azureAdV2Client = AzureAdV2Client(
        environment = env,
        httpClient = httpClientWithProxy
    )

    val oidcClient =
        StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenUrl)

    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    val safClient = SafDokumentClient(env, azureAdV2Client, httpClient)

    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)

    val dokArkivClient = DokArkivClient(env.dokArkivUrl, oidcClient, httpClient)

    val regelClient =
        RegelClient(env.regelEndpointURL, azureAdV2Client, env.syfosmpapirregelScope, httpClient)

    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )

    val msGraphClient = MSGraphClient(environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy)

    private val pdlClient = PdlClient(httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), ""))

    val pdlService = PdlPersonService(pdlClient, azureAdV2Client, env.pdlScope)

    private val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, azureAdV2Client, env.helsenettproxyScope, httpClient)

    val sykmelderService = SykmelderService(norskHelsenettClient, pdlService)

    private val safJournalpostClient = SafJournalpostClient(
        httpClient,
        env.safJournalpostGraphqlPath,
        SafJournalpostClient::class.java.getResource("/graphql/getJournalpostStatus.graphql").readText().replace(Regex("[\n\t]"), "")
    )
    val safJournalpostService = SafJournalpostService(env, azureAdV2Client, safJournalpostClient)
}
