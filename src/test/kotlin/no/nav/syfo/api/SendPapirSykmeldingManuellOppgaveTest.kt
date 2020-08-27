package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.concurrent.Future
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
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
import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.SmRegisteringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.sendPapirSykmeldingManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.util.Authorization
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.Test

@KtorExperimentalAPI
internal class SendPapirSykmeldingManuellOppgaveTest {

    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val database = TestDB()
    private val manuellOppgaveService = ManuellOppgaveService(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val kafkaRecievedSykmeldingProducer = mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>()
    private val session = mockk<Session>()
    private val syfoserviceProducer = mockk<MessageProducer>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val kuhrsarClient = mockk<SarClient>()
    private val serviceuserUsername = "serviceuser"
    private val dokArkivClient = mockk<DokArkivClient>()
    private val textMessage = mockk<TextMessage>()
    private val regelClient = mockk<RegelClient>()
    private val kafkaValidationResultProducer = mockk<KafkaProducers.KafkaValidationResultProducer>()
    private val kafkaManuelTaskProducer = mockk<KafkaProducers.KafkaManuelTaskProducer>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val authorization = mockk<Authorization>()
    private val pdlPersonService = mockk<PdlPersonService>()

    @Test
    internal fun `Regsitering av papirsykmelding happycase`() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId",
                    mqUsername = "username",
                    mqPassword = "password",
                    smregistreringBackendClientSecret = "secret",
                    syfosmpapirregelClientId = "clientid"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                sendPapirSykmeldingManuellOppgave(
                    manuellOppgaveService,
                    kafkaRecievedSykmeldingProducer,
                    session,
                    syfoserviceProducer,
                    oppgaveClient,
                    kuhrsarClient,
                    serviceuserUsername,
                    dokArkivClient,
                    regelClient,
                    pdlPersonService,
                    authorization,
                    "edbmaskin"
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

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns ByteArray(1)
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )
            coEvery { authorization.hasAccess(any(), any(), any()) } returns true
            val oppgaveid = 308076319

            val manuellOppgave = PapirSmRegistering(
                journalpostId = "134",
                fnr = "41424",
                aktorId = "1314",
                dokumentInfoId = "131313",
                datoOpprettet = OffsetDateTime.now(ZoneOffset.UTC),
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

            val smRegisteringManuell = SmRegisteringManuell(
                pasientFnr = "143242345",
                sykmelderFnr = "18459123134",
                perioder = listOf(
                    Periode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now(),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(
                            medisinskArsak = MedisinskArsak(
                                beskrivelse = "test data",
                                arsak = listOf(MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET)
                            ),
                            arbeidsrelatertArsak = null
                        ),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false
                    )
                ),
                medisinskVurdering = MedisinskVurdering(
                    hovedDiagnose = Diagnose(
                        system = "2.16.578.1.12.4.1.1.7170",
                        kode = "A070",
                        tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
                    ),
                    biDiagnoser = listOf(),
                    svangerskap = false,
                    yrkesskade = false,
                    yrkesskadeDato = null,
                    annenFraversArsak = null
                ),
                syketilfelleStartDato = LocalDate.of(2020, 4, 1),
                skjermesForPasient = false,
                arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
                behandletDato = LocalDate.now(),
                andreTiltak = "Neida",
                behandler = Behandler(
                    "Per",
                    "",
                    "Person",
                    "123",
                    "",
                    "",
                    "",
                    Adresse(null, null, null, null, null),
                    ""
                ),
                kontaktMedPasient = KontaktMedPasient(LocalDate.MAX, "Ja nei det."),
                meldingTilArbeidsgiver = "Nei",
                meldingTilNAV = MeldingTilNAV(true, "Ja nei det."),
                navnFastlege = "Per Person",
                prognose = null,
                tiltakArbeidsplassen = "Mer flesk og duppe!",
                tiltakNAV = "Nei",
                utdypendeOpplysninger = null
            )

            coEvery { textMessage.text = any() } returns Unit
            coEvery { session.createTextMessage() } returns textMessage
            coEvery { syfoserviceProducer.send(any()) } returns Unit
            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns "automattopic"
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 1)
            coEvery { oppgaveClient.ferdigStillOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 2)
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
            coEvery { dokArkivClient.oppdaterOgFerdigstillJournalpost(any(), any(), any(), any(), any()) } returns ""
            coEvery { kafkaValidationResultProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic } returns "behandligtopic"
            coEvery { kafkaManuelTaskProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaManuelTaskProducer.sm2013ProduserOppgaveTopic } returns "produseroppgavetopic"
            coEvery { regelClient.valider(any(), any()) } returns ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
            )

            coEvery { pdlPersonService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn(
                    "Billy",
                    "Bob",
                    "Thornton"
                ), listOf(IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("12345", false, "AKTORID"))
            )

            with(handleRequest(HttpMethod.Put, "/api/v1/sendPapirSykmeldingManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                setBody(objectMapper.writeValueAsString(smRegisteringManuell))
            }) {
                response.status() shouldEqual HttpStatusCode.NoContent
            }
        }
    }

    @Test
    internal fun `Regsitering av papirsykmelding fra JSON`() {
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId",
                    mqUsername = "username",
                    mqPassword = "password",
                    smregistreringBackendClientSecret = "secret",
                    syfosmpapirregelClientId = "clientid"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                sendPapirSykmeldingManuellOppgave(
                    manuellOppgaveService,
                    kafkaRecievedSykmeldingProducer,
                    session,
                    syfoserviceProducer,
                    oppgaveClient,
                    kuhrsarClient,
                    serviceuserUsername,
                    dokArkivClient,
                    regelClient,
                    pdlPersonService,
                    authorization,
                    "edbmaskin"
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

            coEvery { safDokumentClient.hentDokument(any(), any(), any(), any(), any()) } returns ByteArray(1)
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                null
            )
            coEvery { authorization.hasAccess(any(), any(), any()) } returns true

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

            val smRegisteringManuell = objectMapper.readValue<SmRegisteringManuell>(String(Files.readAllBytes(Paths.get("src/test/resources/sm_registrering_manuell.json")), StandardCharsets.UTF_8))

            coEvery { textMessage.text = any() } returns Unit
            coEvery { session.createTextMessage() } returns textMessage
            coEvery { syfoserviceProducer.send(any()) } returns Unit
            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns "automattopic"
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 1)
            coEvery { oppgaveClient.ferdigStillOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 2)
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
            coEvery { dokArkivClient.oppdaterOgFerdigstillJournalpost(any(), any(), any(), any(), any()) } returns ""
            coEvery { kafkaValidationResultProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic } returns "behandligtopic"
            coEvery { kafkaManuelTaskProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { kafkaManuelTaskProducer.sm2013ProduserOppgaveTopic } returns "produseroppgavetopic"
            coEvery { regelClient.valider(any(), any()) } returns ValidationResult(
                status = Status.OK,
                ruleHits = emptyList()
            )

            coEvery { pdlPersonService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn("Billy", "Bob", "Thornton"), listOf(
                    IdentInformasjon("12345", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID"))
            )

            with(handleRequest(HttpMethod.Put, "/api/v1/sendPapirSykmeldingManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                setBody(objectMapper.writeValueAsString(smRegisteringManuell))
            }) {
                response.status() shouldEqual HttpStatusCode.NoContent
            }
        }
    }
}
