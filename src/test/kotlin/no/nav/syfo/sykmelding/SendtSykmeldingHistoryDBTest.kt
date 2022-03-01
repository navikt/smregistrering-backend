package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.sykmelding.db.insertSendtSykmeldingHistory
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.util.getReceivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.junit.After
import org.junit.Test
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class SendtSykmeldingHistoryDBTest {
    private val testDb = TestDB()

    @After
    fun beforeEach() {
        testDb.dropData()
    }

    @Test
    fun saveSendtSykmeldingToDB() {

        val sykmeldingId = UUID.randomUUID().toString()
        val manuellOppgave = createManuellOppgave(sykmeldingId = sykmeldingId)
        val sendtSykmeldingHistory = createSendtSykmeldingHistory(sykmeldingId = sykmeldingId)

        testDb.opprettManuellOppgave(manuellOppgave, 123)
        testDb.insertSendtSykmeldingHistory(sendtSykmeldingHistory)
        val sendtSykmeldingHistory1 = testDb.getSendtSykmeldingHistory(sykmeldingId = sykmeldingId)
        sendtSykmeldingHistory1 shouldBeEqualTo sendtSykmeldingHistory
    }

    fun DatabaseInterface.getSendtSykmeldingHistory(sykmeldingId: String): SendtSykmeldingHistory? {
        return connection.use {
            it.prepareStatement(
                """
           select * from sendt_sykmelding_history where sykmelding_id = ? 
        """
            ).use {
                it.setString(1, sykmeldingId)
                it.executeQuery().toSendtSykmeldingHistory()
            }
        }
    }

    private fun ResultSet.toSendtSykmeldingHistory(): SendtSykmeldingHistory? {
        return when (next()) {
            true -> SendtSykmeldingHistory(
                id = getString("id").trim(),
                sykmeldingId = getString("sykmelding_id").trim(),
                ferdigstiltAv = getString("ferdigstilt_av").trim(),
                datoFerdigstilt = OffsetDateTime.ofInstant(getTimestamp("dato_ferdigstilt").toInstant(), ZoneId.of("UTC")),
                receivedSykmelding = objectMapper.readValue(getString("sykmelding"))
            )
            else -> null
        }
    }

    private fun createSendtSykmeldingHistory(sykmeldingId: String): SendtSykmeldingHistory {

        return SendtSykmeldingHistory(
            id = UUID.randomUUID().toString(),
            sykmeldingId = sykmeldingId,
            ferdigstiltAv = "ferdigstiltAv",
            datoFerdigstilt = OffsetDateTime.now(ZoneId.of("UTC")),
            getReceivedSykmelding(
                fnrPasient = "1",
                sykmelderFnr = "2",
                sykmeldingId = sykmeldingId
            )
        )
    }

    private fun createManuellOppgave(sykmeldingId: String): PapirSmRegistering {

        return PapirSmRegistering(
            journalpostId = "134",
            oppgaveId = "123",
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
    }
}
