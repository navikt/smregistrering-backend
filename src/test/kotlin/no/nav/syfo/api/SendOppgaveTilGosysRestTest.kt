package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
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
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime

class SendOppgaveTilGosysRestTest {

    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveService = mockk<ManuellOppgaveService>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val env = mockk<Environment>()

    @Test
    fun sendOppgaveTilGosysOK() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                env, jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                sendOppgaveTilGosys(manuellOppgaveService, authorizationService, oppgaveClient)
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { manuellOppgaveService.ferdigstillSmRegistering(any(), any(), any()) } returns 1

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

            coEvery { manuellOppgaveService.hentManuellOppgaver(any()) } returns listOf(manuellOppgaveDTO)

            coEvery { oppgaveClient.sendOppgaveTilGosys(any(), any(), any()) } returns Oppgave(
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
                response.status() shouldBeEqualTo HttpStatusCode.NoContent
                response.content shouldBe null
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
                sendOppgaveTilGosys(manuellOppgaveService, authorizationService, oppgaveClient)
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { manuellOppgaveService.ferdigstillSmRegistering(any(), any(), any()) } returns 1

            val oppgaveid = 308076319

            coEvery { manuellOppgaveService.hentManuellOppgaver(any()) } returns emptyList()

            coEvery { oppgaveClient.sendOppgaveTilGosys(any(), any(), any()) } returns Oppgave(
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
                response.status() shouldBeEqualTo HttpStatusCode.NoContent
                response.content shouldBe null
            }
        }
    }
}
