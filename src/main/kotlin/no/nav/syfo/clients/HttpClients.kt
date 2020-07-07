package no.nav.syfo.clients

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
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import java.net.ProxySelector
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import org.apache.http.impl.conn.SystemDefaultRoutePlanner

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
            }
        }
        expectSuccess = false
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    private val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    private val httpClient = HttpClient(Apache, config)

    @KtorExperimentalAPI
    val oidcClient =
        StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenUrl)

    @KtorExperimentalAPI
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    @InternalAPI
    @KtorExperimentalAPI
    val safClient = SafDokumentClient(env.hentDokumentUrl, httpClient)

    @KtorExperimentalAPI
    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)

    @KtorExperimentalAPI
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, oidcClient, httpClient)

    @KtorExperimentalAPI
    val accessTokenClient = AccessTokenClient(
        env.aadAccessTokenUrl,
        vaultSecrets.smregistreringBackendClientId,
        vaultSecrets.smregistreringBackendClientSecret,
        httpClientWithProxy
    )

    @KtorExperimentalAPI
    val regelClient =
        RegelClient(env.regelEndpointURL, accessTokenClient, vaultSecrets.syfosmpapirregelClientId, httpClient)

    @KtorExperimentalAPI
    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(env.syfoTilgangsKontrollClientUrl, httpClient)

    val pdlClient = PdlClient(httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), ""))

    @KtorExperimentalAPI
    val pdlService = PdlPersonService(pdlClient, oidcClient)
}
