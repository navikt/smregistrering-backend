package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.PsqlContainerDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.util.LoggingMeta
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.BeforeEach

@KtorExperimentalAPI
class HandleRecivedMessageKtTest {
    private val database = PsqlContainerDatabase.database
    private val oppgaveClient = mockk<OppgaveClient>()
    private val loggingMeta = LoggingMeta("", "", "", "", "")

    @BeforeEach
    fun beforeEachTest() {
        clearMocks(oppgaveClient)
        coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        coEvery { oppgaveClient.patchManuellOppgave(any(), any()) } returns Oppgave(id = 2, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
    }

    @After
    fun after() {
        database.connection.dropData()
    }

    @Test
    fun oppretterIkkeOppgaveHvisFinnesIDatabaseFraFor() {
        val oppgaveId = 1
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId.toString())
        database.opprettManuellOppgave(papirSmRegistrering, oppgaveId)
        runBlocking {
            handleRecivedMessage(papirSmRegistrering, database, oppgaveClient, loggingMeta)
        }

        coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify(exactly = 0) { oppgaveClient.patchManuellOppgave(any(), any()) }
    }

    @Test
    fun oppretterOppgaveHvisOppgaveIdIkkeErInteger() {
        coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        val oppgaveId = "null"
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId)
        runBlocking {
            handleRecivedMessage(papirSmRegistrering, database, oppgaveClient, loggingMeta)
        }

        coVerify { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify(exactly = 0) { oppgaveClient.patchManuellOppgave(any(), any()) }
    }

    @Test
    fun oppdatererOppgaveHvisOppgaveIdErSatt() {
        coEvery { oppgaveClient.patchManuellOppgave(any(), any()) } returns Oppgave(id = 1, oppgavetype = "JFR", aktivDato = LocalDate.now(), prioritet = "")
        val oppgaveId = 1
        val papirSmRegistrering = opprettPapirSmRegistrering(oppgaveId.toString())
        runBlocking {
            handleRecivedMessage(papirSmRegistrering, database, oppgaveClient, loggingMeta)
        }

        coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
        coVerify { oppgaveClient.patchManuellOppgave(any(), any()) }
    }
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
