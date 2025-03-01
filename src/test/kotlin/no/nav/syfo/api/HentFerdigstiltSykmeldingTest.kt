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
import no.nav.syfo.aksessering.api.hentFerdigstiltSykmelding
import no.nav.syfo.application.setupAuth
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.controllers.ReceivedSykmeldingController
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
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.persistering.db.ferdigstillSmRegistering
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.Veileder
import no.nav.syfo.syfosmregister.SyfosmregisterService
import no.nav.syfo.syfosmregister.papirsykmelding.model.PapirsykmeldingDTO
import no.nav.syfo.sykmelding.db.upsertSendtSykmelding
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.util.getReceivedSykmelding
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HentFerdigstiltSykmeldingTest {
    private val database = TestDB()
    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val manuellOppgaveDAO = ManuellOppgaveDAO(database)
    private val safDokumentClient = mockk<SafDokumentClient>()
    private val syfosmregisterService = mockk<SyfosmregisterService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val safJournalpostService = mockk<SafJournalpostService>()
    private val receivedSykmeldingController = mockk<ReceivedSykmeldingController>()
    private val env = mockk<Environment> { coEvery { azureAppClientId } returns "clientId" }

    @AfterEach
    fun after() {
        database.dropData()
    }

    @Test
    fun `Hent sykmelding`() {
        testApplication {
            application {
                coEvery {
                    safDokumentClient.hentDokument(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns "stringy string".toByteArray()
                coEvery { authorizationService.hasSuperuserAccess(any(), any()) } returns true
                coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")

                val oppgaveid = 308076319

                coEvery { syfosmregisterService.hentSykmelding(any()) } returns
                    PapirsykmeldingDTO(
                        pasientFnr = "12345678912",
                        mottattTidspunkt = OffsetDateTime.now(),
                        sykmelding =
                            getReceivedSykmelding(
                                    fnrPasient = "41424",
                                    sykmelderFnr = "12345678912",
                                )
                                .sykmelding,
                    )
                val sykmeldingId = "sykmeldingId"

                coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns false

                val papirSmRegistering =
                    PapirSmRegistering(
                        journalpostId = "134",
                        oppgaveId = oppgaveid.toString(),
                        fnr = "41424",
                        aktorId = "1314",
                        dokumentInfoId = "131313",
                        datoOpprettet = OffsetDateTime.now(),
                        sykmeldingId = sykmeldingId,
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
                                        kode = "007",
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

                val smRegisteringManuell =
                    SmRegistreringManuell(
                        pasientFnr = papirSmRegistering.fnr!!,
                        sykmelderFnr = papirSmRegistering.behandler!!.fnr,
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
                                                                .TILSTAND_HINDRER_AKTIVITET,
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
                            Arbeidsgiver(
                                HarArbeidsgiver.EN_ARBEIDSGIVER,
                                "NAV ikt",
                                "Utvikler",
                                100,
                            ),
                        behandletDato = LocalDate.now(),
                        behandler = papirSmRegistering.behandler!!,
                        kontaktMedPasient =
                            KontaktMedPasient(LocalDate.of(2020, 4, 1), "Ja nei det."),
                        meldingTilArbeidsgiver = "Nei",
                        meldingTilNAV = MeldingTilNAV(true, "Ja nei det."),
                        navnFastlege = "Per Person",
                        harUtdypendeOpplysninger = false,
                    )

                val receivedSykmelding =
                    getReceivedSykmelding(
                        smRegisteringManuell,
                        smRegisteringManuell.pasientFnr,
                        smRegisteringManuell.sykmelderFnr,
                        papirSmRegistering.datoOpprettet!!.toLocalDateTime(),
                        sykmeldingId,
                    )

                database.opprettManuellOppgave(papirSmRegistering, oppgaveid)
                database.upsertSendtSykmelding(receivedSykmelding)
                database.ferdigstillSmRegistering(sykmeldingId, "OK", "ferdigstiltAv", null)

                setupAuth(
                    env,
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                )
                routing {
                    hentFerdigstiltSykmelding(
                        FerdigstiltSykmeldingController(
                            manuellOppgaveDAO,
                            safDokumentClient,
                            syfosmregisterService,
                            authorizationService,
                            safJournalpostService,
                            receivedSykmeldingController,
                        ),
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

            val response =
                client.get("/api/v1/sykmelding/sykmeldingId/ferdigstilt") {
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    header(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(true, response.bodyAsText().contains("journalpostId\":\"134"))
            assertEquals(true, response.bodyAsText().contains("sykmeldingId\":\"sykmeldingId"))
            assertEquals(true, response.bodyAsText().contains("\"aktorId\":\"1314\""))
            assertEquals(true, response.bodyAsText().contains("fnr\":\"12345678912"))
        }
    }
}
