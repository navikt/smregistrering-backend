package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
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
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
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
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldBeEqualTo
import org.junit.After
import org.junit.Test
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime

internal class AuthenticateTest {

    private val database = TestDB()
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()

    private val manuellOppgaveService = ManuellOppgaveService(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val authorization = mockk<AuthorizationService>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val pdlService = mockk<PdlPersonService>()
    private val env = mockk<Environment>() {
        coEvery { azureAppClientId } returns "clientId"
    }

    @After
    fun after() {
        database.dropData()
    }

    @Test
    internal fun `Aksepterer gyldig JWT med riktig audience`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns ByteArray(1)
            coEvery { syfoTilgangsKontrollClient.hasAccess(any(), any()) } returns Tilgang(true)
            coEvery { authorization.hasAccess(any(), any()) } returns true
            coEvery { pdlService.getPdlPerson(any(), any()) } returns PdlPerson(
                Navn("Billy", "Bob", "Thornton"),
                listOf(
                    IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                )
            )

            val oppgaveid = 308076319

            val manuellOppgave = PapirSmRegistering(
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
                    null,
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

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.setupAuth(
                env, jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                authenticate("jwt") {
                    hentPapirSykmeldingManuellOppgave(
                        manuellOppgaveService,
                        safDokumentClient,
                        oppgaveClient,
                        authorization
                    )
                }
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

            with(
                handleRequest(HttpMethod.Get, "/api/v1/oppgave/$oppgaveid") {
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                objectMapper.readValue<PapirManuellOppgave>(response.content!!).oppgaveid shouldBeEqualTo oppgaveid
            }
        }
    }

    @Test
    internal fun `Gyldig JWT med feil audience gir Unauthorized`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns ByteArray(1)

            val oppgaveid = 308076319

            val manuellOppgave = PapirSmRegistering(
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
                    null,
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

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.setupAuth(
                env, jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                authenticate("jwt") {
                    hentPapirSykmeldingManuellOppgave(
                        manuellOppgaveService,
                        safDokumentClient,
                        oppgaveClient,
                        authorization
                    )
                }
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

            with(
                handleRequest(HttpMethod.Get, "/api/v1/oppgave/$oppgaveid") {
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "annenClientId")}")
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                response.content shouldBeEqualTo null
            }
        }
    }
}
