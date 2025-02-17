package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.file.Paths
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HentPapirSykmeldingTest {
    private val database = TestDB()
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveDAO = ManuellOppgaveDAO(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = mockk<OppgaveService>()
    private val smTssClient = mockk<SmtssClient>()
    private val dokArkivClient = mockk<DokArkivClient>()
    private val regelClient = mockk<RegelClient>()
    private val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val sendTilGosysController =
        SendTilGosysController(authorizationService, manuellOppgaveDAO, oppgaveService)

    private val env = mockk<Environment> { coEvery { azureAppClientId } returns "clientId" }

    @AfterEach
    fun after() {
        database.dropData()
    }

    @Test
    fun `Hent oppgave`() {
        testApplication {
            val oppgaveid = 308076319

            application {
                coEvery {
                    safDokumentClient.hentDokument(any(), any(), any(), any(), any())
                } returns ByteArray(1)
                coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

                coEvery { authorizationService.hasAccess(any(), any()) } returns true
                coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

                val manuellOppgave =
                    PapirSmRegistering(
                        journalpostId = "134",
                        oppgaveId = "123",
                        fnr = "41424",
                        aktorId = "1314",
                        dokumentInfoId = "131313",
                        datoOpprettet = LocalDateTime.now(),
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
                                    egetArbeidPaSikt = true,
                                    annetArbeidPaSikt = false,
                                    arbeidFOM = LocalDate.now(),
                                    vurderingsdato = LocalDate.now(),
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
                    hentPapirSykmeldingManuellOppgave(
                        manuellOppgaveDAO,
                        safDokumentClient,
                        sendTilGosysController,
                        authorizationService,
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
                            cause.message ?: "Unknown error"
                        )
                        log.error("Caught exception", cause)
                        throw cause
                    }
                }
            }

            coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns
                Oppgave(
                    id = 123,
                    versjon = 2,
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
            coEvery { smTssClient.findBestTssInfotrygdId(any(), any(), any(), any()) } returns
                "12341"
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
            coEvery { regelClient.valider(any(), any()) } returns
                ValidationResult(
                    status = Status.OK,
                    ruleHits = emptyList(),
                )

            val response =
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )}",
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(true, response.bodyAsText().contains("\"aktorId\":\"1314\""))
            assertEquals(
                true,
                response
                    .bodyAsText()
                    .contains(
                        "\"fornavn\":\"John\",\"mellomnavn\":\"Besserwisser\",\"etternavn\":\"Doe\""
                    )
            )

            val response2 =
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                }
            assertEquals(HttpStatusCode.Unauthorized, response2.status)
        }
    }

    @Test
    fun `Hent papirsykmelding papir_sm_registrering = null`() {
        val oppgaveid = 308076319

        val manuellOppgave =
            PapirSmRegistering(
                journalpostId = "134",
                oppgaveId = "123",
                fnr = "41424",
                aktorId = "1314",
                dokumentInfoId = "131313",
                datoOpprettet = LocalDateTime.now(),
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
                            egetArbeidPaSikt = true,
                            annetArbeidPaSikt = false,
                            arbeidFOM = LocalDate.now(),
                            vurderingsdato = LocalDate.now(),
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

        opprettManuellOppgaveNullPapirsm(database.connection, manuellOppgave, oppgaveid)

        coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns
            Oppgave(
                id = 123,
                versjon = 2,
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

        val hentManuellOppgaver = database.hentManuellOppgaver(oppgaveid)

        assertEquals(1, hentManuellOppgaver.size)
        assertEquals(null, hentManuellOppgaver[0].papirSmRegistering)
    }

    @Test
    fun `Hent oppgave - Hvis hentDokument() kaster feilmelding skal oppgaven sendes tilbake til GOSYS`() {
        testApplication {
            val oppgaveid = 308076319

            application {
                coEvery {
                    safDokumentClient.hentDokument(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } throws SafNotFoundException("Saf returnerte: httpstatus 200")
                coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

                coEvery { authorizationService.hasAccess(any(), any()) } returns true
                coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

                val manuellOppgave =
                    PapirSmRegistering(
                        journalpostId = "134",
                        oppgaveId = "123",
                        fnr = "41424",
                        aktorId = "1314",
                        dokumentInfoId = "131313",
                        datoOpprettet = LocalDateTime.now(),
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
                                    egetArbeidPaSikt = true,
                                    annetArbeidPaSikt = false,
                                    arbeidFOM = LocalDate.now(),
                                    vurderingsdato = LocalDate.now(),
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

            coEvery { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) } returns
                Oppgave(
                    id = 123,
                    versjon = 2,
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
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )}",
                    )
                }

            assertEquals(HttpStatusCode.Gone, response.status)
            assertEquals("SENT_TO_GOSYS", response.bodyAsText())
        }

        coVerify(exactly = 1) { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) }
    }

    @Test
    fun `Hent oppgave - manglende tilgang til dokument`() {
        testApplication {
            val oppgaveid = 308076319

            application {
                coEvery {
                    safDokumentClient.hentDokument(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } throws SafForbiddenException("Du har ikke tilgang")
                coEvery {
                    istilgangskontrollClient.hasAccess(
                        any(),
                        any(),
                    )
                } returns Tilgang(true)

                coEvery { authorizationService.hasAccess(any(), any()) } returns true
                coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

                val manuellOppgave =
                    PapirSmRegistering(
                        journalpostId = "134",
                        oppgaveId = "123",
                        fnr = "41424",
                        aktorId = "1314",
                        dokumentInfoId = "131313",
                        datoOpprettet = LocalDateTime.now(),
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
                                    egetArbeidPaSikt = true,
                                    annetArbeidPaSikt = false,
                                    arbeidFOM = LocalDate.now(),
                                    vurderingsdato = LocalDate.now(),
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

            coEvery { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) } returns
                Oppgave(
                    id = 123,
                    versjon = 2,
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
                client.get("/api/v1/oppgave/$oppgaveid") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            coVerify(exactly = 0) { oppgaveService.sendOppgaveTilGosys(any(), any(), any()) }
        }
    }

    private fun opprettManuellOppgaveNullPapirsm(
        databaseConnection: Connection,
        papirSmRegistering: PapirSmRegistering,
        oppgaveId: Int,
    ) {
        databaseConnection.use { connection ->
            connection
                .prepareStatement(
                    """
            INSERT INTO manuelloppgave(
                id,
                journalpost_id,
                fnr,
                aktor_id,
                dokument_info_id,
                dato_opprettet,
                oppgave_id,
                ferdigstilt,
                papir_sm_registrering
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                )
                .use {
                    it.setString(1, papirSmRegistering.sykmeldingId)
                    it.setString(2, papirSmRegistering.journalpostId)
                    it.setString(3, papirSmRegistering.fnr)
                    it.setString(4, papirSmRegistering.aktorId)
                    it.setString(5, papirSmRegistering.dokumentInfoId)
                    it.setTimestamp(
                        6,
                        Timestamp.from(
                            papirSmRegistering.datoOpprettet?.atZone(ZoneOffset.UTC)?.toInstant()
                        )
                    )
                    it.setInt(7, oppgaveId)
                    it.setBoolean(8, false)
                    it.setObject(
                        9,
                        null
                    ) // Store it all so frontend can present whatever is present
                    it.executeUpdate()
                }

            connection.commit()
        }
    }
}
