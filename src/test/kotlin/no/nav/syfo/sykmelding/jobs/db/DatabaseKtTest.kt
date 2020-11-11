package no.nav.syfo.sykmelding.jobs.db

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.upsertSykmelding
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.util.getReceivedSykmelding
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.amshove.kluent.shouldNotEqual
import org.junit.Test
import org.junit.jupiter.api.BeforeEach

class DatabaseKtTest {

    val testDb = TestDB()
    val sykmeldingId = UUID.randomUUID()
    val newJob = Job(sykmeldingId.toString(), JOB_NAME.SENDT_SYKMELDING, JOB_STATUS.NEW, OffsetDateTime.now(ZoneOffset.UTC))

    @BeforeEach
    fun beforeEach() {
        testDb.connection.dropData()
    }

    @Test
    fun doNotResetJobs() {
        insertSykmelding()
        val inProgress = Job(sykmeldingId = sykmeldingId.toString(), status = JOB_STATUS.IN_PROGRESS, name = JOB_NAME.SENDT_SYKMELDING, updated = OffsetDateTime.now().minusMinutes(59))
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
        val inProgress = Job(sykmeldingId = sykmeldingId.toString(), status = JOB_STATUS.IN_PROGRESS, name = JOB_NAME.SENDT_SYKMELDING, updated = OffsetDateTime.now().minusMinutes(61))
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
        val notUpdated = getReceivedSykmelding(fnrPasient = "4", sykmelderFnr = "2", sykmeldingId = UUID.randomUUID().toString())
        testDb.upsertSykmelding(notUpdated)
        testDb.upsertSykmelding(getReceivedSykmelding(fnrPasient = "3", sykmelderFnr = "2", sykmeldingId = sykmeldingId.toString()))
        val savedSykmelding = testDb.getSykmelding(sykmeldingId.toString())
        val savedNotUpdatedSykmelding = testDb.getSykmelding(notUpdated.sykmelding.id)
        savedSykmelding?.personNrPasient shouldEqual "3"
        savedNotUpdatedSykmelding?.personNrPasient shouldEqual "4"
    }

    @Test
    fun saveJobsToDb() {
        insertSykmelding()
        val jobs = listOf(newJob)
        testDb.insertJobs(jobs)
        val savedJob = testDb.getJob(JOB_STATUS.NEW).first()
        savedJob shouldEqual newJob
    }

    @Test
    fun getJobAndSetInProgress() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val inprogressJob = testDb.getNextJob()
        inprogressJob!!.status shouldEqual JOB_STATUS.IN_PROGRESS
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
            firstJob shouldNotEqual secondJob
        }
    }

    @Test
    fun tesLocking() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob.copy(name = JOB_NAME.SENDT_TO_SYFOSERVICE), newJob))

        val job = testDb.getJobStatus(JOB_STATUS.NEW)
        val job2 = testDb.getJobStatus(JOB_STATUS.NEW)
        val job3 = testDb.getJobStatus(JOB_STATUS.NEW)

        job shouldNotBe null
        job2 shouldNotBe null
        job3 shouldBe null

        job shouldNotEqual job2
    }

    @Test
    fun updateJobStatus() {
        insertSykmelding()
        testDb.insertJobs(listOf(newJob))
        val job = testDb.getNextJob()
        job?.status shouldEqual JOB_STATUS.IN_PROGRESS

        testDb.updateJob(job!!.copy(updated = OffsetDateTime.now(), status = JOB_STATUS.DONE))

        val doneJob = testDb.getJobForSykmeldingId(sykmeldingId = sykmeldingId.toString())

        doneJob.size shouldEqual 1
    }

    private fun insertSykmelding() {
        testDb.upsertSykmelding(getReceivedSykmelding(fnrPasient = "1", sykmelderFnr = "2", sykmeldingId = sykmeldingId.toString()))
    }
}

fun DatabaseInterface.getJobForSykmeldingId(sykmeldingId: String): List<Job?> {
    return connection.use {
        it.prepareStatement("""
           select * from job where sykmelding_id = ? 
        """).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList {
                Job(sykmeldingId = getString("sykmelding_id"),
                        updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                        name = JOB_NAME.valueOf(getString("name")),
                        status = JOB_STATUS.valueOf(getString("status")))
            }
        }
    }
}

fun DatabaseInterface.getJob(status: JOB_STATUS): List<Job?> {
    return connection.use {
        it.prepareStatement("""
           select * from job where status = ? 
        """).use {
            it.setString(1, status.name)
            it.executeQuery().toList {
                Job(sykmeldingId = getString("sykmelding_id"),
                        updated = getTimestamp("updated").toInstant().atOffset(ZoneOffset.UTC),
                        name = JOB_NAME.valueOf(getString("name")),
                        status = JOB_STATUS.valueOf(getString("status")))
            }
        }
    }
}
