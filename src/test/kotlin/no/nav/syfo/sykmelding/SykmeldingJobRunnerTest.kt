package no.nav.syfo.sykmelding

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.sykmelding.jobs.db.getJobForSykmeldingId
import no.nav.syfo.sykmelding.jobs.db.upsertJobs
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.testutil.PsqlContainerDatabase
import no.nav.syfo.testutil.dropData
import no.nav.syfo.util.getReceivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.junit.After
import org.junit.Test
import java.time.OffsetDateTime

class SykmeldingJobRunnerTest {
    private val testDB = PsqlContainerDatabase.database
    val applicationState = ApplicationState(true, true)
    val sykmeldingJobService = spyk(SykmeldingJobService(testDB))
    val kafkaReceivedSykmeldingProducer = mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>(relaxed = true)
    val kafkaSyfoserviceProducer = mockk<KafkaProducers.KafkaSyfoserviceProducer>(relaxed = true)
    val service = SykmeldingJobRunner(
        applicationState,
        sykmeldingJobService,
        kafkaReceivedSykmeldingProducer,
        kafkaSyfoserviceProducer
    )

    init {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(3_000) } returns Unit
    }

    @After
    fun afterTest() {
        testDB.connection.dropData()
    }

    @Test
    fun processJob() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "1")
        sykmeldingJobService.upsertSykmelding(sykmelding)
        sykmeldingJobService.createJobs(sykmelding)
        var jobCount = 0
        every { sykmeldingJobService.getNextJob() } answers {
            if (jobCount++ > 1) {
                applicationState.ready = false
            }
            callOriginal()
        }
        runBlocking {
            service.startJobRunner()
        }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        verify(exactly = 1) { kafkaSyfoserviceProducer.producer.send(any()) }
        jobs.first { it?.name == JOB_NAME.SENDT_SYKMELDING }?.status shouldBeEqualTo JOB_STATUS.DONE
        jobs.first { it?.name == JOB_NAME.SENDT_TO_SYFOSERVICE }?.status shouldBeEqualTo JOB_STATUS.DONE
    }

    @Test
    fun jobFails() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sykmeldingJobService.upsertSykmelding(sykmelding)
        sykmeldingJobService.createJobs(sykmelding)
        every { kafkaSyfoserviceProducer.producer.send(any()) } throws Exception("Some error")
        var jobCount = 0
        every { sykmeldingJobService.getNextJob() } answers {
            if (jobCount++ > 1) applicationState.ready = false
            callOriginal()
        }
        runBlocking {
            service.startJobRunner()
        }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)

        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        verify(exactly = 1) { kafkaSyfoserviceProducer.producer.send(any()) }
        jobs.first { it?.name == JOB_NAME.SENDT_SYKMELDING }?.status shouldBeEqualTo JOB_STATUS.DONE
        jobs.first { it?.name == JOB_NAME.SENDT_TO_SYFOSERVICE }?.status shouldBeEqualTo JOB_STATUS.IN_PROGRESS
    }

    @Test
    fun runJobsDoNotResetInProgressJobsBeforeTimeout() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sykmeldingJobService.upsertSykmelding(sykmelding)
        testDB.upsertJobs(
            listOf(
                Job(
                    sykmelding.sykmelding.id,
                    JOB_NAME.SENDT_TO_SYFOSERVICE,
                    JOB_STATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(59)
                ),
                Job(
                    sykmelding.sykmelding.id,
                    JOB_NAME.SENDT_SYKMELDING,
                    JOB_STATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(59)
                )
            )
        )
        var jobCount = 0
        every { sykmeldingJobService.getNextJob() } answers {
            if (jobCount++ > 5) applicationState.ready = false
            callOriginal()
        }
        runBlocking {
            service.startJobRunner()
        }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 0) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        verify(exactly = 0) { kafkaSyfoserviceProducer.producer.send(any()) }
        jobs.first { it?.name == JOB_NAME.SENDT_SYKMELDING }?.status shouldBeEqualTo JOB_STATUS.IN_PROGRESS
        jobs.first { it?.name == JOB_NAME.SENDT_TO_SYFOSERVICE }?.status shouldBeEqualTo JOB_STATUS.IN_PROGRESS
    }

    @Test
    fun resetAndRunInProgressJobs() {
        val sykmelding = getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2")
        sykmeldingJobService.upsertSykmelding(sykmelding)
        testDB.upsertJobs(
            listOf(
                Job(
                    sykmelding.sykmelding.id,
                    JOB_NAME.SENDT_TO_SYFOSERVICE,
                    JOB_STATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(61)
                ),
                Job(
                    sykmelding.sykmelding.id,
                    JOB_NAME.SENDT_SYKMELDING,
                    JOB_STATUS.IN_PROGRESS,
                    OffsetDateTime.now().minusMinutes(61)
                )
            )
        )
        var jobCount = 0
        every { sykmeldingJobService.getNextJob() } answers {
            if (jobCount++ > 5) applicationState.ready = false
            callOriginal()
        }
        runBlocking {
            service.startJobRunner()
        }
        val jobs = testDB.getJobForSykmeldingId(sykmelding.sykmelding.id)
        verify(exactly = 1) { kafkaReceivedSykmeldingProducer.producer.send(any()) }
        verify(exactly = 1) { kafkaSyfoserviceProducer.producer.send(any()) }
        jobs.first { it?.name == JOB_NAME.SENDT_SYKMELDING }?.status shouldBeEqualTo JOB_STATUS.DONE
        jobs.first { it?.name == JOB_NAME.SENDT_TO_SYFOSERVICE }?.status shouldBeEqualTo JOB_STATUS.DONE
    }
}
