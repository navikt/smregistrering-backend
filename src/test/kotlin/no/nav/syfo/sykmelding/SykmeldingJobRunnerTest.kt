package no.nav.syfo.sykmelding

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.KafkaProducers
import no.nav.syfo.sykmelding.jobs.db.getJobForSykmeldingId
import no.nav.syfo.sykmelding.jobs.db.insertJobs
import no.nav.syfo.sykmelding.jobs.model.JOBNAME
import no.nav.syfo.sykmelding.jobs.model.JOBSTATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.util.getReceivedSykmelding
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SykmeldingJobRunnerTest {
    private val testDB = TestDB()
    val applicationState = ApplicationState(true, true)
    val sendtSykmeldingService = spyk(SendtSykmeldingService(testDB))
    val kafkaReceivedSykmeldingProducer =
        mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>(relaxed = true)
    val service =
        SykmeldingJobRunner(
            applicationState,
            sendtSykmeldingService,
            kafkaReceivedSykmeldingProducer,
        )

    init {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(15_000) } returns Unit
    }
    @AfterEach
    fun afterTest() {
        testDB.connection.dropData()
    }

    @Test
    fun processJob() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "1")
        sendtSykmeldingService.upsertSendtSykmelding(sykmelding)
        sendtSykmeldingService.createJobs(sykmelding)
        var jobCount = 0
        every { sendtSykmeldingService.getNextJob() } answers
            {
                if (jobCount++ > 1) {
                    applicationState.ready = false
                }
                callOriginal()
            }
        runBlocking { service.startJobRunner() }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        assertEquals(JOBSTATUS.DONE, jobs.first { it?.name == JOBNAME.SENDT_SYKMELDING }?.status)
    }

    @Test
    fun jobFails() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sendtSykmeldingService.upsertSendtSykmelding(sykmelding)
        sendtSykmeldingService.createJobs(sykmelding)
        every { kafkaReceivedSykmeldingProducer.producer.send(any()) } throws
            Exception("Some error")
        var jobCount = 0
        every { sendtSykmeldingService.getNextJob() } answers
            {
                if (jobCount++ > 1) applicationState.ready = false
                callOriginal()
            }
        runBlocking { service.startJobRunner() }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)

        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        assertEquals(
            JOBSTATUS.IN_PROGRESS,
            jobs.first { it?.name == JOBNAME.SENDT_SYKMELDING }?.status
        )
    }

    @Test
    fun runJobsDoNotResetInProgressJobsBeforeTimeout() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sendtSykmeldingService.upsertSendtSykmelding(sykmelding)
        testDB.insertJobs(
            listOf(
                Job(
                    sykmelding.sykmelding.id,
                    JOBNAME.SENDT_SYKMELDING,
                    JOBSTATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(59),
                ),
            ),
        )
        var jobCount = 0
        every { sendtSykmeldingService.getNextJob() } answers
            {
                if (jobCount++ > 5) applicationState.ready = false
                callOriginal()
            }
        runBlocking { service.startJobRunner() }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 0) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        assertEquals(
            JOBSTATUS.IN_PROGRESS,
            jobs.first { it?.name == JOBNAME.SENDT_SYKMELDING }?.status
        )
    }

    @Test
    fun resetAndRunInProgressJobs() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sendtSykmeldingService.upsertSendtSykmelding(sykmelding)
        testDB.insertJobs(
            listOf(
                Job(
                    sykmelding.sykmelding.id,
                    JOBNAME.SENDT_SYKMELDING,
                    JOBSTATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(61),
                ),
            ),
        )
        var jobCount = 0
        every { sendtSykmeldingService.getNextJob() } answers
            {
                if (jobCount++ > 5) applicationState.ready = false
                callOriginal()
            }
        runBlocking { service.startJobRunner() }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        assertEquals(JOBSTATUS.DONE, jobs.first { it?.name == JOBNAME.SENDT_SYKMELDING }?.status)
    }
}
