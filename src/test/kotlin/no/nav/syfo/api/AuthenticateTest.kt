package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
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
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AuthenticateTest {

    private val database = TestDB()
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()

    private val manuellOppgaveDAO = ManuellOppgaveDAO(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = OppgaveService(oppgaveClient)
    private val sendTilGosysController =
        SendTilGosysController(authorizationService, manuellOppgaveDAO, oppgaveService)

    private val pdlService = mockk<PdlPersonService>()
    private val env = mockk<Environment>() { coEvery { azureAppClientId } returns "clientId" }

    @AfterEach
    fun after() {
        database.dropData()
    }

    @Test
    internal fun `Aksepterer gyldig JWT med riktig audience`() {
        testApplication {
            val oppgaveid = 308076319
            application {
                coEvery {
                    safDokumentClient.hentDokument(any(), any(), any(), any(), any())
                } returns ByteArray(1)
                coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)
                coEvery { authorizationService.hasAccess(any(), any()) } returns true
                coEvery { pdlService.getPdlPerson(any(), any()) } returns
                    PdlPerson(
                        Navn("Billy", "Bob", "Thornton"),
                        listOf(
                            IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345", false, "AKTORID"),
                        ),
                    )

                val manuellOppgave =
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
                                null,
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
                                    Diagnose(
                                        system = "System",
                                        tekst = "Farlig sykdom",
                                        kode = "007"
                                    ),
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

                database.opprettManuellOppgave(manuellOppgave, oppgaveid)

                setupAuth(
                    env,
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                )
                routing {
                    authenticate("jwt") {
                        hentPapirSykmeldingManuellOppgave(
                            manuellOppgaveDAO,
                            safDokumentClient,
                            sendTilGosysController,
                            authorizationService,
                        )
                    }
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
                            cause.message ?: "Unknown error"
                        )
                        log.error("Caught exception", cause)
                        throw cause
                    }
                }
            }

            val response =
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                oppgaveid,
                objectMapper.readValue<PapirManuellOppgave>(response.bodyAsText()).oppgaveid
            )
        }
    }

    @Test
    internal fun `Gyldig JWT med feil audience gir Unauthorized`() {
        testApplication {
            val oppgaveid = 308076319
            application {
                coEvery {
                    safDokumentClient.hentDokument(any(), any(), any(), any(), any())
                } returns ByteArray(1)

                val manuellOppgave =
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
                                null,
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
                                    Diagnose(
                                        system = "System",
                                        tekst = "Farlig sykdom",
                                        kode = "007"
                                    ),
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

                database.opprettManuellOppgave(manuellOppgave, oppgaveid)

                setupAuth(
                    env,
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                )
                routing {
                    authenticate("jwt") {
                        hentPapirSykmeldingManuellOppgave(
                            manuellOppgaveDAO,
                            safDokumentClient,
                            sendTilGosysController,
                            authorizationService,
                        )
                    }
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
                            cause.message ?: "Unknown error"
                        )
                        log.error("Caught exception", cause)
                        throw cause
                    }
                }
            }

            val response =
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "annenClientId")}")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("", response.bodyAsText())
        }
    }
}
