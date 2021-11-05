package no.nav.syfo.sykmelding

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.upsertSykmelding
import no.nav.syfo.sykmelding.jobs.db.getNextJob
import no.nav.syfo.sykmelding.jobs.db.insertJobs
import no.nav.syfo.sykmelding.jobs.db.resetJobs
import no.nav.syfo.sykmelding.jobs.db.updateJob
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import java.time.OffsetDateTime

class SykmeldingJobService(private val databaseInterface: DatabaseInterface) {

    fun getNextJob(): Job? {
        return databaseInterface.getNextJob()
    }

    fun finishJob(job: Job) {
        databaseInterface.updateJob(job)
    }

    fun upsertSykmelding(receivedSykmelding: ReceivedSykmelding) {
        databaseInterface.upsertSykmelding(receivedSykmelding)
    }

    fun createJobs(receivedSykmelding: ReceivedSykmelding) {
        val syfoserviceJob = Job(sykmeldingId = receivedSykmelding.sykmelding.id, status = JOB_STATUS.NEW, updated = OffsetDateTime.now(), name = JOB_NAME.SENDT_TO_SYFOSERVICE)
        val sendSykmeldingJob = Job(sykmeldingId = receivedSykmelding.sykmelding.id, status = JOB_STATUS.NEW, updated = OffsetDateTime.now(), name = JOB_NAME.SENDT_SYKMELDING)
        log.info("Creating jobs:\n$syfoserviceJob\n$sendSykmeldingJob")
        databaseInterface.insertJobs(listOf(syfoserviceJob, sendSykmeldingJob))
    }

    fun getReceivedSykmelding(sykmeldingId: String): ReceivedSykmelding? {
        return databaseInterface.getSykmelding(sykmeldingId)
    }

    fun resetHangingJobs() {
        val numbers = databaseInterface.resetJobs()
        if (numbers > 0) {
            log.info("Reset $numbers jobs FROM IN_PROGRESS to NEW")
        }
    }
}
