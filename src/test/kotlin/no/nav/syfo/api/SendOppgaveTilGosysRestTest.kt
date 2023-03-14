package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.persistering.api.sendOppgaveTilGosys
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.testutil.generateJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime

class SendOppgaveTilGosysRestTest {

    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveDAO = mockk<ManuellOppgaveDAO>()
    private val oppgaveService = mockk<OppgaveService>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val sendTilGosysController = SendTilGosysController(authorizationService, manuellOppgaveDAO, oppgaveService)

    private val env = mockk<Environment>()

    @Test
    fun sendOppgaveTilGosysOK() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                env, jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                sendOppgaveTilGosys(manuellOppgaveDAO, sendTilGosysController, authorizationService)
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }
            coEvery { syfoTilgangsKontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { manuellOppgaveDAO.ferdigstillSmRegistering(any(), any(), any()) } returns 1

            val oppgaveid = 308076319

            val papirSmRegistering = PapirSmRegistering(
                journalpostId = "134",
                oppgaveId = "123",
                fnr = "41424",
                aktorId = "1314",
                dokumentInfoId = "131313",
                datoOpprettet = OffsetDateTime.now(),
                sykmeldingId = "1344444",
                syketilfelleStartDato = LocalDate.now(),
                behandler = Behandler(
                    "John",
                    "Besserwisser",
                    "Doe",
                    "123",
                    "12345678912",
                    "hpr",
                    null,
                    Adresse(null, null, null, null, null),
                    "12345"
                ),
                kontaktMedPasient = null,
                meldingTilArbeidsgiver = null,
                meldingTilNAV = null,
                andreTiltak = "Nei",
                tiltakNAV = "Nei",
                tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
                utdypendeOpplysninger = null,
                prognose = Prognose(
                    true,
                    "Nei",
                    ErIArbeid(
                        true,
                        false,
                        LocalDate.now(),
                        LocalDate.now()
                    ),
                    null
                ),
                medisinskVurdering = MedisinskVurdering(
                    hovedDiagnose = Diagnose(system = "System", tekst = "Farlig sykdom", kode = "007"),
                    biDiagnoser = emptyList(),
                    annenFraversArsak = null,
                    yrkesskadeDato = null,
                    yrkesskade = false,
                    svangerskap = false
                ),
                arbeidsgiver = null,
                behandletTidspunkt = null,
                perioder = null,
                skjermesForPasient = false
            )

            val manuellOppgaveDTO = ManuellOppgaveDTO(
                journalpostId = "journalpostId",
                fnr = "fnr",
                aktorId = "aktorId",
                dokumentInfoId = null,
                datoOpprettet = null,
                sykmeldingId = "sykmeldingsId",
                oppgaveid = oppgaveid,
                papirSmRegistering = papirSmRegistering,
                ferdigstilt = false,
                pdfPapirSykmelding = null
            )

            coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns listOf(manuellOppgaveDTO)

            coEvery { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) } returns Oppgave(
                id = oppgaveid,
                versjon = 1,
                tilordnetRessurs = "",
                tildeltEnhetsnr = "",
                journalpostId = "",
                aktivDato = LocalDate.MAX,
                aktoerId = "",
                behandlesAvApplikasjon = "",
                behandlingstype = "",
                beskrivelse = "",
                fristFerdigstillelse = null,
                oppgavetype = "",
                opprettetAvEnhetsnr = "",
                prioritet = "",
                saksreferanse = "",
                tema = "",
                status = "OPPRETTET"
            )

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/tilgosys") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())
                assertEquals(null, response.content)
            }
        }
    }

    @Test
    fun sendOppgaveTilGosysOppgaveFerdigstilt() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                env, jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                sendOppgaveTilGosys(manuellOppgaveDAO, sendTilGosysController, authorizationService)
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }
            coEvery { syfoTilgangsKontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { manuellOppgaveDAO.ferdigstillSmRegistering(any(), any(), any()) } returns 1

            val oppgaveid = 308076319

            coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns emptyList()

            coEvery { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) } returns Oppgave(
                id = oppgaveid,
                versjon = 1,
                tilordnetRessurs = "",
                tildeltEnhetsnr = "",
                journalpostId = "",
                aktivDato = LocalDate.MAX,
                aktoerId = "",
                behandlesAvApplikasjon = "",
                behandlingstype = "",
                beskrivelse = "",
                fristFerdigstillelse = null,
                oppgavetype = "",
                opprettetAvEnhetsnr = "",
                prioritet = "",
                saksreferanse = "",
                tema = "",
                status = "OPPRETTET"
            )

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/tilgosys") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())
                assertEquals(null, response.content)
            }
        }
    }
}
