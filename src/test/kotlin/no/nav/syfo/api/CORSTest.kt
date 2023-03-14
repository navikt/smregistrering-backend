package no.nav.syfo.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.InternalAPI
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CORSTest {

    @InternalAPI
    @Test
    internal fun `No origin header`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("https"))
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm alive! :)", response.content)
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Wrong origin header`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("https"))
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Get, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "invalid-host")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm ready! :)", response.content)
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Wrong origin header is empty`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("https"))
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Get, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm ready! :)", response.content)
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Simple Request`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("http", "https"))
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Get, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://smregistrering.nais.preprod.local")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("https://smregistrering.nais.preprod.local", response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm ready! :)", response.content)
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Simple Null`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                anyHost()
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Get, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "null")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm ready! :)", response.content)
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Pre flight custom host`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("http", "https"))
                allowNonSimpleContentTypes = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Options, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://smregistrering.nais.preprod.local")
                    addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("https://smregistrering.nais.preprod.local", response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("Content-Type", response.headers[HttpHeaders.AccessControlAllowHeaders])
                assertEquals(HttpHeaders.Origin, response.headers[HttpHeaders.Vary])
            }
        }
    }

    @InternalAPI
    @Test
    internal fun `Simple credentials`() {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("smregistrering.nais.preprod.local", schemes = listOf("https"))
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(
                handleRequest(HttpMethod.Options, "/internal/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://smregistrering.nais.preprod.local")
                    addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                }
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("https://smregistrering.nais.preprod.local", response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(HttpHeaders.Origin, response.headers[HttpHeaders.Vary])
                assertEquals("true", response.headers[HttpHeaders.AccessControlAllowCredentials])
            }
        }
    }
}
