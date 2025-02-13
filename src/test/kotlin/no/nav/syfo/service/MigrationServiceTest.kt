package no.nav.syfo.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull
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
    fun `getMigrationObject should merge elements correctly`() {
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

        every { manuellOppgaveDAO.getUmigrertManuellOppgave() } returns oppgave1
        every { sendtSykmeldingService.getSykmeldingHistory("123") } returns history1

        val result = migrationService.getMigrationObject()

        assertNotNull(result)
        assertEquals("123", result.sykmeldingId)

        verify { manuellOppgaveDAO.getUmigrertManuellOppgave() }
        verify { sendtSykmeldingService.getSykmeldingHistory("123") }
    }
}
