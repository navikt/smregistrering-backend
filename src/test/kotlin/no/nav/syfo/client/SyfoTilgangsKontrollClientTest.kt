package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class SyfoTilgangsKontrollClientTest {

    @Test
    internal fun `Skal returnere harTilgang til true`() {
        val httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }

        val mockHttpServerPort = ServerSocket(0).use { it.localPort }
        val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
        val pasientFnr = "123145"
        val mockServer = embeddedServer(Netty, mockHttpServerPort) {
            install(ContentNegotiation) {
                jackson {}
            }
            routing {
                get("/api/tilgang/navident/bruker/$pasientFnr") {
                    when {
                        call.request.headers["Authorization"] == "Bearer sdfsdfsfs" -> call.respond(
                            Tilgang(
                                harTilgang = true,
                                begrunnelse = null
                            )
                        )
                        else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                    }
                }
            }
        }.start()

        val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(mockHttpServerUrl, httpClient)

        runBlocking {
            val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            tilgang?.harTilgang shouldEqual true
            mockServer.stop(TimeUnit.SECONDS.toMillis(30), TimeUnit.SECONDS.toMillis(30))
        }
    }
}