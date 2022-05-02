package no.nav.syfo.controllers

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OppgaveClient
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
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.ferdigstillSmRegistering
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.insertSendtSykmeldingHistory
import no.nav.syfo.sykmelding.db.upsertSendtSykmelding
import no.nav.syfo.sykmelding.getSendtSykmeldingHistory
import no.nav.syfo.sykmelding.jobs.db.getJobForSykmeldingId
import no.nav.syfo.sykmelding.jobs.db.insertJobs
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getReceivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReceivedSykmeldingControllerTest {

    private val database = TestDB()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = OppgaveService(oppgaveClient)
    private val controller = ReceivedSykmeldingController(database, oppgaveService)

    private val loggingMeta = LoggingMeta("", "", "", "", "")

    @BeforeEach
    fun beforeEachTest() {
        clearMocks(oppgaveClient)
        coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } returns Oppgave(id = 2, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
    }

    @After
    fun after() {
        database.dropData()
    }

    @Test
    fun oppretterIkkeOppgaveHvisFinnesIDatabaseFraFor() {
        val oppgaveId = 1
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId.toString())
        database.opprettManuellOppgave(papirSmRegistrering, oppgaveId)
        runBlocking {
            controller.handleReceivedSykmelding(papirSmRegistrering, loggingMeta)
        }

        coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify(exactly = 0) { oppgaveClient.oppdaterOppgave(any(), any()) }
    }

    @Test
    fun oppretterOppgaveHvisOppgaveIdIkkeErInteger() {
        coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        val oppgaveId = "null"
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId)
        runBlocking {
            controller.handleReceivedSykmelding(papirSmRegistrering, loggingMeta)
        }

        coVerify { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify(exactly = 0) { oppgaveClient.oppdaterOppgave(any(), any()) }
    }

    @Test
    fun oppdatererOppgaveHvisOppgaveIdErSatt() {
        coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        val oppgaveId = 1
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId.toString())
        runBlocking {
            controller.handleReceivedSykmelding(papirSmRegistrering, loggingMeta)
        }

        coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify { oppgaveClient.oppdaterOppgave(any(), any()) }
    }

    @Test
    fun sletterSykmelding() {
        val sykmeldingId = opprettFerdigstiltPapirsykmelding()

        database.erOpprettManuellOppgave(sykmeldingId) shouldBeEqualTo true
        database.getSykmelding(sykmeldingId) shouldNotBeEqualTo null
        database.getJobForSykmeldingId(sykmeldingId) shouldNotBeEqualTo emptyList()
        database.getSendtSykmeldingHistory(sykmeldingId) shouldNotBeEqualTo null

        controller.slettSykmelding(sykmeldingId)

        database.erOpprettManuellOppgave(sykmeldingId) shouldBeEqualTo false
        database.getSykmelding(sykmeldingId) shouldBeEqualTo null
        database.getJobForSykmeldingId(sykmeldingId) shouldBeEqualTo emptyList()
        database.getSendtSykmeldingHistory(sykmeldingId) shouldBeEqualTo null
    }

    @Test
    fun sletterKunAngittSykmelding() {
        val sykmeldingId = opprettFerdigstiltPapirsykmelding()

        controller.slettSykmelding(UUID.randomUUID().toString())

        database.erOpprettManuellOppgave(sykmeldingId) shouldBeEqualTo true
        database.getSykmelding(sykmeldingId) shouldNotBeEqualTo null
        database.getJobForSykmeldingId(sykmeldingId) shouldNotBeEqualTo emptyList()
        database.getSendtSykmeldingHistory(sykmeldingId) shouldNotBeEqualTo null
    }

    fun opprettPapirSmRegistrering(oppgaveId: String): PapirSmRegistering =
        PapirSmRegistering(
            journalpostId = "134",
            oppgaveId = oppgaveId,
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

    fun opprettFerdigstiltPapirsykmelding(): String {
        val sykmeldingId = UUID.randomUUID().toString()
        val oppgaveid = 308076319

        val papirSmRegistering = PapirSmRegistering(
            journalpostId = "134",
            oppgaveId = oppgaveid.toString(),
            fnr = "41424",
            aktorId = "1314",
            dokumentInfoId = "131313",
            datoOpprettet = OffsetDateTime.now(),
            sykmeldingId = sykmeldingId,
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

        val smRegisteringManuell = SmRegistreringManuell(
            pasientFnr = papirSmRegistering.fnr!!,
            sykmelderFnr = papirSmRegistering.behandler!!.fnr,
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
            behandler = papirSmRegistering.behandler!!,
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 4, 1), "Ja nei det."),
            meldingTilArbeidsgiver = "Nei",
            meldingTilNAV = MeldingTilNAV(true, "Ja nei det."),
            navnFastlege = "Per Person",
            harUtdypendeOpplysninger = false
        )

        val receivedSykmelding = getReceivedSykmelding(
            smRegisteringManuell,
            smRegisteringManuell.pasientFnr,
            smRegisteringManuell.sykmelderFnr,
            papirSmRegistering.datoOpprettet!!.toLocalDateTime(),
            sykmeldingId
        )

        database.opprettManuellOppgave(papirSmRegistering, oppgaveid)
        database.upsertSendtSykmelding(receivedSykmelding)
        database.insertSendtSykmeldingHistory(SendtSykmeldingHistory(UUID.randomUUID().toString(), sykmeldingId, "noen", OffsetDateTime.now(ZoneOffset.UTC), receivedSykmelding))
        database.ferdigstillSmRegistering(sykmeldingId, "OK", "ferdigstiltAv", null)
        database.insertJobs(listOf(Job(sykmeldingId, JOB_NAME.SENDT_SYKMELDING, JOB_STATUS.DONE, OffsetDateTime.now(ZoneOffset.UTC))))

        return sykmeldingId
    }
}
