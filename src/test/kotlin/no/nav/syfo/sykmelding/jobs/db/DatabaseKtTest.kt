package no.nav.syfo.sykmelding.jobs.db

import io.mockk.coEvery
import io.mockk.mockkStatic
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
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.After
import org.junit.Test
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

    @After
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
            updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusMinutes(59)
        )
        testDb.insertJobs(listOf(inProgress))

        val job = testDb.getNextJob()
        job shouldBe null

        val updated = testDb.resetJobs()
        updated shouldBe 0

        val nextJob = testDb.getNextJob()
        nextJob shouldBe null
    }

    @Test
    fun resetJobs() {
        insertSykmelding()
        val inProgress = Job(
            sykmeldingId = sykmeldingId.toString(),
            status = JOB_STATUS.IN_PROGRESS,
            name = JOB_NAME.SENDT_SYKMELDING,
            updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusMinutes(61)
        )
        testDb.insertJobs(listOf(inProgress))

        val job = testDb.getNextJob()
        job shouldBe null

        val updated = testDb.resetJobs()
        updated shouldBe 1

        val nextJob = testDb.getNextJob()
        nextJob shouldNotBe null
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
                sykmeldingId = sykmeldingId.toString()
            )
        )
        val savedSykmelding = testDb.getSykmelding(sykmeldingId.toString())
        val savedNotUpdatedSykmelding = testDb.getSykmelding(notUpdated.sykmelding.id)
        savedSykmelding?.personNrPasient shouldBeEqualTo "3"
        savedNotUpdatedSykmelding?.personNrPasient shouldBeEqualTo "4"
    }

    @Test
    fun saveJobsToDb() {
        insertSykmelding()
        val jobs = listOf(newJob)
        testDb.insertJobs(jobs)
        val savedJob = testDb.getJob(JOB_STATUS.NEW).first()
        savedJob shouldBeEqualTo newJob
    }

    @Test
    fun getJobAndSetInProgress() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val inprogressJob = testDb.getNextJob()
        inprogressJob!!.status shouldBeEqualTo JOB_STATUS.IN_PROGRESS
    }

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
            firstJob shouldNotBeEqualTo secondJob
        }
    }

    @Test
    fun updateJobStatus() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val job = testDb.getNextJob()
        job?.status shouldBeEqualTo JOB_STATUS.IN_PROGRESS

        testDb.updateJob(job!!.copy(updated = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)), status = JOB_STATUS.DONE))

        val doneJob = testDb.getJobForSykmeldingId(sykmeldingId = sykmeldingId.toString())

        doneJob.size shouldBeEqualTo 1
    }

    private fun insertSykmelding() {
        testDb.upsertSendtSykmelding(
            getReceivedSykmelding(
                fnrPasient = "1",
                sykmelderFnr = "2",
                sykmeldingId = sykmeldingId.toString()
            )
        )
    }
}

fun DatabaseInterface.getJobForSykmeldingId(sykmeldingId: String): List<Job?> {
    return connection.use {
        it.prepareStatement(
            """
           select * from job where sykmelding_id = ? 
        """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList {
                Job(
                    sykmeldingId = getString("sykmelding_id"),
                    updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                    name = JOB_NAME.valueOf(getString("name")),
                    status = JOB_STATUS.valueOf(getString("status"))
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
        """
        ).use {
            it.setString(1, status.name)
            it.executeQuery().toList {
                Job(
                    sykmeldingId = getString("sykmelding_id"),
                    updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                    name = JOB_NAME.valueOf(getString("name")),
                    status = JOB_STATUS.valueOf(getString("status"))
                )
            }
        }
    }
}
