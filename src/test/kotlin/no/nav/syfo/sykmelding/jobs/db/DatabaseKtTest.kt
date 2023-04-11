package no.nav.syfo.sykmelding.jobs.db

import io.mockk.coEvery
import io.mockk.mockkStatic
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.upsertSendtSykmelding
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.util.getReceivedSykmelding
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DatabaseKtTest {
    private val testDb = TestDB()
    val sykmeldingId = UUID.randomUUID()
    val newJob =
        Job(sykmeldingId.toString(), JOB_NAME.SENDT_SYKMELDING, JOB_STATUS.NEW, OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)))

    init {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(3_000) } returns Unit
    }

    @AfterEach
    fun beforeEach() {
        testDb.dropData()
    }

    @Test
    fun doNotResetJobs() {
        insertSykmelding()
        val inProgress = Job(
            sykmeldingId = sykmeldingId.toString(),
            status = JOB_STATUS.IN_PROGRESS,
            name = JOB_NAME.SENDT_SYKMELDING,
            updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusMinutes(59),
        )
        testDb.insertJobs(listOf(inProgress))

        val job = testDb.getNextJob()
        assertNull(job)

        val updated = testDb.resetJobs()
        assertEquals(0, updated)

        val nextJob = testDb.getNextJob()
        assertNull(nextJob)
    }

    @Test
    fun resetJobs() {
        insertSykmelding()
        val inProgress = Job(
            sykmeldingId = sykmeldingId.toString(),
            status = JOB_STATUS.IN_PROGRESS,
            name = JOB_NAME.SENDT_SYKMELDING,
            updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusMinutes(61),
        )
        testDb.insertJobs(listOf(inProgress))

        val job = testDb.getNextJob()
        assertNull(job)

        val updated = testDb.resetJobs()
        assertEquals(1, updated)

        val nextJob = testDb.getNextJob()
        assertNotNull(nextJob)
    }

    @Test
    fun upsertSykmelding() {
        insertSykmelding()
        val notUpdated =
            getReceivedSykmelding(fnrPasient = "4", sykmelderFnr = "2", sykmeldingId = UUID.randomUUID().toString())
        testDb.upsertSendtSykmelding(notUpdated)
        testDb.upsertSendtSykmelding(
            getReceivedSykmelding(
                fnrPasient = "3",
                sykmelderFnr = "2",
                sykmeldingId = sykmeldingId.toString(),
            ),
        )
        val savedSykmelding = testDb.getSykmelding(sykmeldingId.toString())
        val savedNotUpdatedSykmelding = testDb.getSykmelding(notUpdated.sykmelding.id)
        assertEquals("3", savedSykmelding?.personNrPasient)
        assertEquals("4", savedNotUpdatedSykmelding?.personNrPasient)
    }

    @Test
    fun saveJobsToDb() {
        insertSykmelding()
        val jobs = listOf(newJob)
        testDb.insertJobs(jobs)
        val savedJob = testDb.getJob(JOB_STATUS.NEW).first()
        assertEquals(newJob, savedJob)
    }

    @Test
    fun getJobAndSetInProgress() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val inprogressJob = testDb.getNextJob()
        assertEquals(JOB_STATUS.IN_PROGRESS, inprogressJob!!.status)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun getDifferentJobThreads() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob.copy(name = JOB_NAME.SENDT_TO_SYFOSERVICE), newJob))

        runBlocking {
            var firstJob: Job? = null
            var secondJob: Job? = null
            val job = GlobalScope.launch {
                firstJob = testDb.getNextJob()
            }
            val job2 = GlobalScope.launch {
                secondJob = testDb.getNextJob()
            }

            job.join()
            job2.join()
            assertEquals(secondJob?.equals(firstJob) ?: false, false)
        }
    }

    @Test
    fun updateJobStatus() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val job = testDb.getNextJob()
        assertEquals(JOB_STATUS.IN_PROGRESS, job?.status)

        testDb.updateJob(job!!.copy(updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)), status = JOB_STATUS.DONE))

        val doneJob = testDb.getJobForSykmeldingId(sykmeldingId = sykmeldingId.toString())

        assertEquals(1, doneJob.size)
    }

    private fun insertSykmelding() {
        testDb.upsertSendtSykmelding(
            getReceivedSykmelding(
                fnrPasient = "1",
                sykmelderFnr = "2",
                sykmeldingId = sykmeldingId.toString(),
            ),
        )
    }
}

fun DatabaseInterface.getJobForSykmeldingId(sykmeldingId: String): List<Job?> {
    return connection.use {
        it.prepareStatement(
            """
           select * from job where sykmelding_id = ? 
        """,
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList {
                Job(
                    sykmeldingId = getString("sykmelding_id"),
                    updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                    name = JOB_NAME.valueOf(getString("name")),
                    status = JOB_STATUS.valueOf(getString("status")),
                )
            }
        }
    }
}

fun DatabaseInterface.getJob(status: JOB_STATUS): List<Job?> {
    return connection.use {
        it.prepareStatement(
            """
           select * from job where status = ? 
        """,
        ).use {
            it.setString(1, status.name)
            it.executeQuery().toList {
                Job(
                    sykmeldingId = getString("sykmelding_id"),
                    updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                    name = JOB_NAME.valueOf(getString("name")),
                    status = JOB_STATUS.valueOf(getString("status")),
                )
            }
        }
    }
}
