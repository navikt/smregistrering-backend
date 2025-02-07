package no.nav.syfo.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.test.Test
import no.nav.syfo.kafka.ReceivedSykmeldingWithTimestamp
import no.nav.syfo.model.ManuellOppgaveDTOSykDig
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.sykmelding.MigrationService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.util.getReceivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals

class MigrationServiceTest {
    private val manuellOppgaveDAO = mockk<ManuellOppgaveDAO>()
    private val sendtSykmeldingService = mockk<SendtSykmeldingService>()
    private val migrationService = MigrationService(sendtSykmeldingService, manuellOppgaveDAO)

    @Test
    fun `getAllMigrationObjects should merge elements correctly`() {
        val oppgave1 =
            ManuellOppgaveDTOSykDig(
                "id1",
                "123",
                null,
                null,
                null,
                "123",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        val history1 =
            listOf(
                SendtSykmeldingHistory(
                    "id1",
                    "123",
                    "ferdigstiltAv1",
                    LocalDateTime.now(),
                    getReceivedSykmelding(
                        sykmeldingId = "123",
                        fnrPasient = "123",
                        sykmelderFnr = "1234",
                    ),
                ),
                SendtSykmeldingHistory(
                    "id1",
                    "123",
                    "ferdigstiltAv1",
                    LocalDateTime.now().plusDays(2),
                    getReceivedSykmelding(
                        sykmeldingId = "123",
                        fnrPasient = "123",
                        sykmelderFnr = "1234",
                    ),
                ),
            )
        val sm1 =
            ReceivedSykmeldingWithTimestamp(
                getReceivedSykmelding(
                    sykmeldingId = "123",
                    fnrPasient = "123",
                    sykmelderFnr = "1234",
                ),
                OffsetDateTime.now(),
            )

        every { manuellOppgaveDAO.hentAlleManuellOppgaverSykDig() } returns listOf(oppgave1)
        every { sendtSykmeldingService.getAllReceivedSykmeldingHistory() } returns history1
        every { sendtSykmeldingService.getAllReceivedSykmeldingWithTimestamp() } returns listOf(sm1)

        val result = migrationService.getAllMigrationObjects()

        assertEquals(1, result.size)
        assertEquals("123", result[0].sykmeldingId)
        assertEquals(1, result[0].manuellOppgave.size)
        assertEquals(2, result[0].sendtSykmeldingHistory?.size)

        verify { manuellOppgaveDAO.hentAlleManuellOppgaverSykDig() }
        verify { sendtSykmeldingService.getAllReceivedSykmeldingHistory() }
        verify { sendtSykmeldingService.getAllReceivedSykmeldingWithTimestamp() }
    }
}
