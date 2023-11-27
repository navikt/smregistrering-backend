package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.ContentType
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
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Future
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.Kode
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.ValidationException
import no.nav.syfo.persistering.api.sendPapirSykmeldingManuellOppgave
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendPapirSykmeldingTest {
    private val database = TestDB()
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveDAO = ManuellOppgaveDAO(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val kafkaRecievedSykmeldingProducer =
        mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = OppgaveService(oppgaveClient)
    private val smTssClient = mockk<SmtssClient>()
    private val dokArkivClient = mockk<DokArkivClient>()
    private val safJournalpostService = mockk<SafJournalpostService>()
    private val regelClient = mockk<RegelClient>()
    private val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
    private val authorizationService = mockk<AuthorizationService>()
    private val pdlPersonService = mockk<PdlPersonService>()
    private val sykmelderService = mockk<SykmelderService>()
    private val sendtSykmeldingService = mockk<SendtSykmeldingService>(relaxed = true)
    private val journalpostService = JournalpostService(dokArkivClient, safJournalpostService)
    private val environment = mockk<Environment>()

    @AfterEach
    fun after() {
        database.dropData()
    }

    @Test
    fun `Registrering av papirsykmelding happycase`() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                this@SendPapirSykmeldingTest.environment,
                jwkProvider,
                "https://sts.issuer.net/myid",
            )
            application.routing {
                sendPapirSykmeldingManuellOppgave(
                    SendPapirsykmeldingController(
                        sykmelderService,
                        pdlPersonService,
                        smTssClient,
                        regelClient,
                        authorizationService,
                        sendtSykmeldingService,
                        oppgaveService,
                        journalpostService,
                        manuellOppgaveDAO,
                    ),
                )
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
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        cause.message ?: "Unknown error"
                    )
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns
                ByteArray(1)
            coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)

            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            val oppgaveid = 308076319

            val manuellOppgave =
                PapirSmRegistering(
                    journalpostId = "134",
                    oppgaveId = "123",
                    fnr = "41424",
                    aktorId = "1314",
                    dokumentInfoId = "131313",
                    datoOpprettet = OffsetDateTime.now(ZoneOffset.UTC),
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

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            val smRegisteringManuell =
                SmRegistreringManuell(
                    pasientFnr = "143242345",
                    sykmelderFnr = "18459123134",
                    perioder =
                        listOf(
                            Periode(
                                fom = LocalDate.now(),
                                tom = LocalDate.now(),
                                aktivitetIkkeMulig =
                                    AktivitetIkkeMulig(
                                        medisinskArsak =
                                            MedisinskArsak(
                                                beskrivelse = "test data",
                                                arsak =
                                                    listOf(
                                                        MedisinskArsakType
                                                            .TILSTAND_HINDRER_AKTIVITET
                                                    ),
                                            ),
                                        arbeidsrelatertArsak = null,
                                    ),
                                avventendeInnspillTilArbeidsgiver = null,
                                behandlingsdager = null,
                                gradert = null,
                                reisetilskudd = false,
                            ),
                        ),
                    medisinskVurdering =
                        MedisinskVurdering(
                            hovedDiagnose =
                                Diagnose(
                                    system = "2.16.578.1.12.4.1.1.7170",
                                    kode = "A070",
                                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
                                ),
                            biDiagnoser = listOf(),
                            svangerskap = false,
                            yrkesskade = false,
                            yrkesskadeDato = null,
                            annenFraversArsak = null,
                        ),
                    syketilfelleStartDato = LocalDate.of(2020, 4, 1),
                    skjermesForPasient = false,
                    arbeidsgiver =
                        Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
                    behandletDato = LocalDate.now(),
                    behandler =
                        Behandler(
                            "Per",
                            "",
                            "Person",
                            "123",
                            "",
                            "hpr",
                            "",
                            Adresse(null, null, null, null, null),
                            "",
                        ),
                    kontaktMedPasient = KontaktMedPasient(LocalDate.MAX, "Ja nei det."),
                    meldingTilArbeidsgiver = "Nei",
                    meldingTilNAV = MeldingTilNAV(true, "Ja nei det."),
                    navnFastlege = "Per Person",
                    harUtdypendeOpplysninger = false,
                )

            val future = mockk<Future<RecordMetadata>>()
            coEvery { future.get() } returns mockk()
            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns
                mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns
                "automattopic"
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
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
            coEvery { smTssClient.findBestTssInfotrygdId(any(), any(), any(), any()) } returns
                "12341"
            coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns true
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

            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns
                PdlPerson(
                    Navn(
                        "Billy",
                        "Bob",
                        "Thornton",
                    ),
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

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/send") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(
                        HttpHeaders.Authorization,
                        "Bearer ${generateJWT(
                            "2",
                            "clientId",
                            Claim("preferred_username", "firstname.lastname@nav.no"),
                        )}",
                    )
                    setBody(objectMapper.writeValueAsString(smRegisteringManuell))
                },
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            verify(exactly = 1) { sendtSykmeldingService.upsertSendtSykmelding(any()) }
            verify(exactly = 1) { sendtSykmeldingService.createJobs(any()) }
            verify(exactly = 1) { sendtSykmeldingService.insertSendtSykmeldingHistory(any()) }
        }
    }

    @Test
    fun `Registrering av papirsykmelding fra JSON`() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                this@SendPapirSykmeldingTest.environment,
                jwkProvider,
                "https://sts.issuer.net/myid",
            )
            application.routing {
                sendPapirSykmeldingManuellOppgave(
                    SendPapirsykmeldingController(
                        sykmelderService,
                        pdlPersonService,
                        smTssClient,
                        regelClient,
                        authorizationService,
                        sendtSykmeldingService,
                        oppgaveService,
                        journalpostService,
                        manuellOppgaveDAO,
                    ),
                )
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
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        cause.message ?: "Unknown error"
                    )
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns
                ByteArray(1)
            coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)
            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            val oppgaveid = 308076319

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
                    prognose = null,
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

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            val smRegisteringManuell =
                objectMapper.readValue<SmRegistreringManuell>(
                    String(
                        Files.readAllBytes(
                            Paths.get("src/test/resources/sm_registrering_manuell.json")
                        ),
                        StandardCharsets.UTF_8,
                    ),
                )

            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns
                mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns
                "automattopic"

            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
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
            coEvery { smTssClient.findBestTssInfotrygdId(any(), any(), any(), any()) } returns
                "12341"
            coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns true
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

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/send") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(
                        HttpHeaders.Authorization,
                        "Bearer ${generateJWT(
                            "2",
                            "clientId",
                            Claim("preferred_username", "firstname.lastname@nav.no"),
                        )}",
                    )
                    setBody(objectMapper.writeValueAsString(smRegisteringManuell))
                },
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/send") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    setBody(objectMapper.writeValueAsString(smRegisteringManuell))
                },
            ) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }

            verify(exactly = 1) { sendtSykmeldingService.upsertSendtSykmelding(any()) }
            verify(exactly = 1) { sendtSykmeldingService.createJobs(any()) }
        }
    }

    @Test
    fun `Registrering av papirsykmelding med ugyldig JSON`() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                this@SendPapirSykmeldingTest.environment,
                jwkProvider,
                "https://sts.issuer.net/myid",
            )
            application.routing {
                sendPapirSykmeldingManuellOppgave(
                    SendPapirsykmeldingController(
                        sykmelderService,
                        pdlPersonService,
                        smTssClient,
                        regelClient,
                        authorizationService,
                        sendtSykmeldingService,
                        oppgaveService,
                        journalpostService,
                        manuellOppgaveDAO,
                    ),
                )
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<ValidationException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, cause.validationResult)
                    log.error("Caught ValidationException", cause)
                }

                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        cause.message ?: "Unknown error"
                    )
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns
                ByteArray(1)
            coEvery { istilgangskontrollClient.hasAccess(any(), any()) } returns Tilgang(true)
            coEvery { authorizationService.hasAccess(any(), any()) } returns true
            coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

            val oppgaveid = 308076319

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

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            val smRegisteringManuell =
                objectMapper.readValue<SmRegistreringManuell>(
                    String(
                        Files.readAllBytes(
                            Paths.get(
                                "src/test/resources/sm_registrering_manuell_ugyldig_validering.json"
                            )
                        ),
                        StandardCharsets.UTF_8,
                    ),
                )

            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns
                mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns
                "automattopic"
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
                    godkjenninger =
                        listOf(
                            Godkjenning(
                                Kode(aktiv = true, oid = 1, verdi = "FOO"),
                                Kode(aktiv = true, oid = 1, verdi = "BAR"),
                            ),
                        ),
                )

            with(
                handleRequest(HttpMethod.Post, "/api/v1/oppgave/$oppgaveid/send") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    setBody(objectMapper.writeValueAsString(smRegisteringManuell))
                },
            ) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertEquals(ContentType.Application.Json, response.contentType())
                assertEquals(false, response.content.isNullOrEmpty())
                assertEquals(
                    listOf(
                        "{\"status\":\"MANUAL_PROCESSING\",\"ruleHits\":[{\"ruleName\":\"periodeValidation\",\"messageForSender\":\"Sykmeldingen må ha minst én periode oppgitt for å være gyldig\",\"messageForUser\":\"Sykmelder har gjort en feil i utfyllingen av sykmeldingen.\",\"ruleStatus\":\"MANUAL_PROCESSING\"}]}"
                    ),
                    response.content!!.lines()
                )
            }
        }
    }
}
