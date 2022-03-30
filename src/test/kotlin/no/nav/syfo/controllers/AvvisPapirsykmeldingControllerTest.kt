package no.nav.syfo.controllers

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.JournalpostService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.Veileder
import no.nav.syfo.sykmelder.service.SykmelderService
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class AvvisPapirsykmeldingControllerTest {

    private val manuellOppgaveDAO = mockk<ManuellOppgaveDAO>()
    private val authorizationService = mockk<AuthorizationService>()
    private val sykmelderService = mockk<SykmelderService>()
    private val oppgaveClient = mockk<OppgaveClient>()
    private val oppgaveService = OppgaveService(oppgaveClient)
    private val safJournalpostService = mockk<SafJournalpostService>()
    private val dokArkivClient = mockk<DokArkivClient>()
    private val journalpostService = JournalpostService(dokArkivClient, safJournalpostService)
    private val avvisPapirsykmeldingController = AvvisPapirsykmeldingController(authorizationService, sykmelderService, manuellOppgaveDAO, oppgaveService, journalpostService)

    private val opprinneligBeskrivelse = "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
        "Viktig beskrivelse!\n" +
        "\n" +
        "Manuell registrering av sykmelding mottatt på papir"
    private val veileder = Veileder("Z999999")
    private val enhet = "0101"
    private val timestamp = LocalDateTime.of(2022, 2, 4, 11, 23)

    @Test
    fun avisPapirsykmeldingHappyCase() {

        coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns listOf(getManuellOppgaveDTO(1))
        coEvery { authorizationService.hasAccess(any(), any()) } returns true
        coEvery { authorizationService.getVeileder(any()) } returns Veileder("U1337")
        coEvery { oppgaveService.hentOppgave(any(), any()) } returns getOppgave(1)
        coEvery { safJournalpostService.erJournalfoert(any(), any()) } returns false
        coEvery { dokArkivClient.oppdaterOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns ""
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns getOppgave()
        coEvery { oppgaveClient.ferdigstillOppgave(any(), any()) } returns getOppgave()
        coEvery { manuellOppgaveDAO.ferdigstillSmRegistering(any(), any(), any(), any()) } returns 1

        runBlocking {
            val avvisPapirsykmelding = avvisPapirsykmeldingController.avvisPapirsykmelding(1, "token", "123", "reason")
            avvisPapirsykmelding.httpStatusCode shouldBeEqualTo HttpStatusCode.NoContent
        }
    }

    @Test
    fun avisPapirsykmeldingVeilederIkkeTilgang() {

        coEvery { manuellOppgaveDAO.hentManuellOppgaver(any()) } returns listOf(getManuellOppgaveDTO(1))
        coEvery { authorizationService.hasAccess(any(), any()) } returns false

        runBlocking {
            val avvisPapirsykmelding = avvisPapirsykmeldingController.avvisPapirsykmelding(1, "token", "123", "reason")
            avvisPapirsykmelding.httpStatusCode shouldBeEqualTo HttpStatusCode.Forbidden
        }
    }

    private fun getOppgave(): Oppgave {
        return Oppgave(
            id = 1, versjon = 1,
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
    }

    @Test
    fun lagOppgavebeskrivelseLagerRiktigBeskrivelseMedAvvisningsarsak() {
        val avvisSykmeldingReason = "Feil avventende periode"
        val oppdatertBeskrivelse = avvisPapirsykmeldingController.lagOppgavebeskrivelse(avvisSykmeldingReason, opprinneligBeskrivelse, veileder, enhet, timestamp)
        oppdatertBeskrivelse shouldBeEqualTo "--- 04.02.2022 11:23 Z999999, 0101 ---\n" +
            "Avvist papirsykmelding med årsak: Feil avventende periode\n" +
            "\n" +
            "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
            "Viktig beskrivelse!\n" +
            "\n" +
            "Manuell registrering av sykmelding mottatt på papir"
    }

    @Test
    fun lagOppgavebeskrivelseLagerRiktigBeskrivelseUtenAvvisningsarsak() {
        val avvisSykmeldingReason = null

        val oppdatertBeskrivelse = avvisPapirsykmeldingController.lagOppgavebeskrivelse(
            avvisSykmeldingReason,
            opprinneligBeskrivelse,
            veileder,
            enhet,
            timestamp
        )

        oppdatertBeskrivelse shouldBeEqualTo "--- 04.02.2022 11:23 Z999999, 0101 ---\n" +
            "Avvist papirsykmelding uten oppgitt årsak.\n" +
            "\n" +
            "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
            "Viktig beskrivelse!\n" +
            "\n" +
            "Manuell registrering av sykmelding mottatt på papir"
    }

    fun getManuellOppgaveDTO(oppgaveId: Int): ManuellOppgaveDTO {
        return ManuellOppgaveDTO(
            journalpostId = "journalpostId",
            fnr = "fnr",
            aktorId = "aktorId",
            dokumentInfoId = null,
            datoOpprettet = null,
            sykmeldingId = "sykmeldingsId",
            oppgaveid = oppgaveId,
            papirSmRegistering = getPapirSm(),
            ferdigstilt = false,
            pdfPapirSykmelding = null
        )
    }

    fun getPapirSm(): PapirSmRegistering {

        return PapirSmRegistering(
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
    }

    fun getOppgave(oppgaveId: Int): Oppgave {
        return Oppgave(
            id = oppgaveId, versjon = 1,
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
    }
}
