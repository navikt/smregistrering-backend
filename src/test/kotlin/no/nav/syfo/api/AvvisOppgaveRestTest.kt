package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.controllers.AvvisPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AvvisSykmeldingRequest
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.avvisOppgave
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.generateJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvvisOppgaveRestTest {

    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveDAO = mockk<ManuellOppgaveDAO>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = OppgaveService(oppgaveClient)
    private val dokArkivClient = mockk<DokArkivClient>()
    private val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val pdlPersonService = mockk<PdlPersonService>()
    private val sykmelderService = mockk<SykmelderService>()
    private val safJournalpostService = mockk<SafJournalpostService>()
    private val journalpostService = JournalpostService(dokArkivClient, safJournalpostService)
    private val avvisPapirsykmeldingController =
        AvvisPapirsykmeldingController(
            authorizationService,
            sykmelderService,
            manuellOppgaveDAO,
            oppgaveService,
            journalpostService,
        )
    private val env = mockk<Environment>()

    @Test
    fun avvisOppgaveOK() {
        testApplication {
            application {
                setupAuth(
                    env,
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                )
                routing {
                    avvisOppgave(
                        avvisPapirsykmeldingController = avvisPapirsykmeldingController,
                    )
                }

                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    }
                }
                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            cause.message ?: "Unknown error",
                        )
                        log.error("Caught exception", cause)
                        throw cause
                    }
                }
            }

            coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns
                PdlPerson(
                    Navn("Billy", "Bob", "Thornton"),
                    listOf(
                        IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("12345", false, "AKTORID"),
                    ),
                )

            coEvery { sykmelderService.hentSykmelder(any(), any()) } returns
                Sykmelder(
                    aktorId = "aktorid",
                    etternavn = "Thornton",
                    fornavn = "Billy",
                    mellomnavn = "Bob",
                    fnr = "12345",
                    hprNummer = "hpr",
                    godkjenninger = null,
                )

            coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns true

            coEvery {
                manuellOppgaveDAO.ferdigstillSmRegistering(any(), any(), any(), any())
            } returns 1

            val oppgaveid = 308076319

            val papirSmRegistering =
                PapirSmRegistering(
                    journalpostId = "134",
                    oppgaveId = "123",
                    fnr = "41424",
                    aktorId = "1314",
                    dokumentInfoId = "131313",
                    datoOpprettet = OffsetDateTime.now(),
                    sykmeldingId = "1344444",
                    syketilfelleStartDato = LocalDate.now(),
                    behandler =
                        Behandler(
                            "John",
                            "Besserwisser",
                            "Doe",
                            "123",
                            "12345678912",
                            "hpr",
                            null,
                            Adresse(null, null, null, null, null),
                            "12345",
                        ),
                    kontaktMedPasient = null,
                    meldingTilArbeidsgiver = null,
                    meldingTilNAV = null,
                    andreTiltak = "Nei",
                    tiltakNAV = "Nei",
                    tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
                    utdypendeOpplysninger = null,
                    prognose =
                        Prognose(
                            true,
                            "Nei",
                            ErIArbeid(
                                true,
                                false,
                                LocalDate.now(),
                                LocalDate.now(),
                            ),
                            null,
                        ),
                    medisinskVurdering =
                        MedisinskVurdering(
                            hovedDiagnose =
                                Diagnose(system = "System", tekst = "Farlig sykdom", kode = "007"),
                            biDiagnoser = emptyList(),
                            annenFraversArsak = null,
                            yrkesskadeDato = null,
                            yrkesskade = false,
                            svangerskap = false,
                        ),
                    arbeidsgiver = null,
                    behandletTidspunkt = null,
                    perioder = null,
                    skjermesForPasient = false,
                )

            val manuellOppgaveDTO =
                ManuellOppgaveDTO(
                    journalpostId = "journalpostId",
                    fnr = "fnr",
                    aktorId = "aktorId",
                    dokumentInfoId = null,
                    datoOpprettet = null,
                    sykmeldingId = "sykmeldingsId",
                    oppgaveid = oppgaveid,
                    papirSmRegistering = papirSmRegistering,
                    ferdigstilt = false,
                    pdfPapirSykmelding = null,
                )

            coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns
                listOf(manuellOppgaveDTO)
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
                Oppgave(
                    id = 123,
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
                    status = "OPPRETTET",
                )

            val avvisSykmeldingRequest = AvvisSykmeldingRequest("Foo bar reason")

            coEvery {
                dokArkivClient.oppdaterOgFerdigstillJournalpost(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns ""
            coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns
                Oppgave(
                    id = 123,
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
                    status = "OPPRETTET",
                )

            val response =
                client.post("/api/v1/oppgave/$oppgaveid/avvis") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header("X-Nav-Enhet", "1234")
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                            generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )
                        }",
                    )
                    setBody(objectMapper.writeValueAsString(avvisSykmeldingRequest))
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("", response.bodyAsText())
        }
    }

    @Test
    fun avvisOppgaveAlleredeFerdigstilt() {
        testApplication {
            application {
                setupAuth(
                    env,
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                )
                routing {
                    avvisOppgave(
                        avvisPapirsykmeldingController = avvisPapirsykmeldingController,
                    )
                }

                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    }
                }
                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            cause.message ?: "Unknown error",
                        )
                        log.error("Caught exception", cause)
                        throw cause
                    }
                }
            }

            coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns
                PdlPerson(
                    Navn("Billy", "Bob", "Thornton"),
                    listOf(
                        IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("12345", false, "AKTORID"),
                    ),
                )

            coEvery { sykmelderService.hentSykmelder(any(), any()) } returns
                Sykmelder(
                    aktorId = "aktorid",
                    etternavn = "Thornton",
                    fornavn = "Billy",
                    mellomnavn = "Bob",
                    fnr = "12345",
                    hprNummer = "hpr",
                    godkjenninger = null,
                )

            coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns true

            coEvery {
                manuellOppgaveDAO.ferdigstillSmRegistering(
                    any(),
                    any(),
                    any(),
                )
            } returns 1

            val oppgaveid = 308076319

            coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns emptyList()
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
                Oppgave(
                    id = 123,
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
                    status = "OPPRETTET",
                )

            val avvisSykmeldingRequest = AvvisSykmeldingRequest("Foo bar reason")

            coEvery {
                dokArkivClient.oppdaterOgFerdigstillJournalpost(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns ""
            coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns
                Oppgave(
                    id = 123,
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
                    status = "OPPRETTET",
                )

            val response =
                client.post("/api/v1/oppgave/$oppgaveid/avvis") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header("X-Nav-Enhet", "1234")
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                            generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )
                        }",
                    )
                    setBody(objectMapper.writeValueAsString(avvisSykmeldingRequest))
                }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("", response.bodyAsText())
        }
    }
}
