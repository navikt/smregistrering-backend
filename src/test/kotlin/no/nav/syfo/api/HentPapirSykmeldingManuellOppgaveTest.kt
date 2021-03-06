package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.file.Paths
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.concurrent.Future
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.client.Veileder
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.IdentInfo
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.PsqlContainerDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.After
import org.junit.Test

@KtorExperimentalAPI
internal class HentPapirSykmeldingManuellOppgaveTest {
    private val database = PsqlContainerDatabase.database
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveService = ManuellOppgaveService(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val kafkaRecievedSykmeldingProducer = mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val kuhrsarClient = mockk<SarClient>()
    private val aktoerIdClient = mockk<AktoerIdClient>()
    private val dokArkivClient = mockk<DokArkivClient>()
    private val regelClient = mockk<RegelClient>()
    private val kafkaValidationResultProducer = mockk<KafkaProducers.KafkaValidationResultProducer>()
    private val kafkaManuelTaskProducer = mockk<KafkaProducers.KafkaManuelTaskProducer>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val syfoTilgangsKontrollService = mockk<AuthorizationService>()

    @After
    fun after() {
        database.connection.dropData()
    }

    @Test
    fun `Hent oppgave`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns ByteArray(1)
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )

            coEvery { syfoTilgangsKontrollService.hasAccess(any(), any()) } returns true
            coEvery { syfoTilgangsKontrollService.getVeileder(any()) } returns Veileder("U1337")

            val oppgaveid = 308076319

            val manuellOppgave = PapirSmRegistering(
                journalpostId = "134",
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
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId",
                    smregistreringBackendClientSecret = "secret",
                    syfosmpapirregelClientId = "clientid"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                hentPapirSykmeldingManuellOppgave(
                    manuellOppgaveService,
                    safDokumentClient,
                    oppgaveClient,
                    syfoTilgangsKontrollService
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
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns "automattopic"
            coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns Oppgave(
                id = 123, versjon = 2,
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
            coEvery { aktoerIdClient.getAktoerIds(any(), any(), any()) } returns mapOf(
                Pair(
                    "143242345", IdentInfoResult(
                        identer = listOf(IdentInfo("645514141444", "asd", true)),
                        feilmelding = null
                    )
                ), Pair(
                    "18459123134", IdentInfoResult(
                        identer = listOf(IdentInfo("6455142134", "asd", true)),
                        feilmelding = null
                    )
                )
            )
            coEvery { kuhrsarClient.getSamhandler(any()) } returns listOf(
                Samhandler(
                    samh_id = "12341",
                    navn = "Perhansen",
                    samh_type_kode = "fALE",
                    behandling_utfall_kode = "auto",
                    unntatt_veiledning = "1",
                    godkjent_manuell_krav = "0",
                    ikke_godkjent_for_refusjon = "0",
                    godkjent_egenandel_refusjon = "0",
                    godkjent_for_fil = "0",
                    endringslogg_tidspunkt_siste = Calendar.getInstance().time,
                    samh_praksis = listOf(),
                    samh_ident = listOf()
                )
            )
            coEvery { dokArkivClient.oppdaterOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns ""
            coEvery { kafkaValidationResultProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic } returns "behandligtopic"
            coEvery { kafkaManuelTaskProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaManuelTaskProducer.sm2013ProduserOppgaveTopic } returns "produseroppgavetopic"
            coEvery { regelClient.valider(any(), any()) } returns ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
            )

            with(handleRequest(HttpMethod.Get, "/api/v1/oppgave/$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content?.contains("\"aktorId\":\"1314\"") shouldEqual true
                response.content?.contains("\"fornavn\":\"John\",\"mellomnavn\":\"Besserwisser\",\"etternavn\":\"Doe\"") shouldEqual true
            }

            with(handleRequest(HttpMethod.Get, "/api/v1/oppgave/$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
            }) {
                response.status() shouldEqual HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Hent papirsykmelding papir_sm_registrering = null`() {

        val oppgaveid = 308076319

        val manuellOppgave = PapirSmRegistering(
            journalpostId = "134",
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

        opprettManuellOppgaveNullPapirsm(database.connection, manuellOppgave, oppgaveid)

        coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
        coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns "automattopic"
        coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns Oppgave(
            id = 123, versjon = 2,
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
        coEvery { aktoerIdClient.getAktoerIds(any(), any(), any()) } returns mapOf(
            Pair(
                "143242345", IdentInfoResult(
                    identer = listOf(IdentInfo("645514141444", "asd", true)),
                    feilmelding = null
                )
            ), Pair(
                "18459123134", IdentInfoResult(
                    identer = listOf(IdentInfo("6455142134", "asd", true)),
                    feilmelding = null
                )
            )
        )

        val hentManuellOppgaver = database.hentManuellOppgaver(oppgaveid)

        hentManuellOppgaver.size shouldEqual 1
        hentManuellOppgaver[0].papirSmRegistering shouldEqual null
    }

    @Test
    fun `Hent oppgave - hentDokument() kaster feilmelding`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } throws SafNotFoundException("Saf returnerte: httpstatus 200")
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )

            coEvery { syfoTilgangsKontrollService.hasAccess(any(), any()) } returns true
            coEvery { syfoTilgangsKontrollService.getVeileder(any()) } returns Veileder("U1337")

            val oppgaveid = 308076319

            val manuellOppgave = PapirSmRegistering(
                journalpostId = "134",
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
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId",
                    smregistreringBackendClientSecret = "secret",
                    syfosmpapirregelClientId = "clientid"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                authenticate("jwt") {
                    hentPapirSykmeldingManuellOppgave(
                        manuellOppgaveService,
                        safDokumentClient,
                        oppgaveClient,
                        syfoTilgangsKontrollService
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

            coEvery { oppgaveClient.sendOppgaveTilGosys(any(), any(), any()) } returns Oppgave(
                id = 123, versjon = 2,
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

            with(handleRequest(HttpMethod.Get, "/api/v1/oppgave/$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.Gone
                response.content shouldEqual "SENT_TO_GOSYS"
            }

            coVerify(exactly = 1) { oppgaveClient.sendOppgaveTilGosys(any(), any(), any()) }
        }
    }

    private fun opprettManuellOppgaveNullPapirsm(
        connection: Connection,
        papirSmRegistering: PapirSmRegistering,
        oppgaveId: Int
    ) {
        connection.use { connection ->
            connection.prepareStatement(
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
            """
            ).use {
                it.setString(1, papirSmRegistering.sykmeldingId)
                it.setString(2, papirSmRegistering.journalpostId)
                it.setString(3, papirSmRegistering.fnr)
                it.setString(4, papirSmRegistering.aktorId)
                it.setString(5, papirSmRegistering.dokumentInfoId)
                it.setTimestamp(6, Timestamp.from(papirSmRegistering.datoOpprettet?.toInstant()))
                it.setInt(7, oppgaveId)
                it.setBoolean(8, false)
                it.setObject(9, null) // Store it all so frontend can present whatever is present
                it.executeUpdate()
            }

            connection.commit()
        }
    }
}
