package no.nav.syfo.sykmelding

import java.time.OffsetDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.ReceivedSykmeldingWithTimestamp
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SendtSykmeldingHistory
import no.nav.syfo.sykmelding.db.geAlltSendtSykmeldingHistory
import no.nav.syfo.sykmelding.db.getSendtSykmeldingHistory
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.getSykmeldingWithTimestamp
import no.nav.syfo.sykmelding.db.insertSendtSykmeldingHistory
import no.nav.syfo.sykmelding.db.upsertSendtSykmelding
import no.nav.syfo.sykmelding.jobs.db.getNextJob
import no.nav.syfo.sykmelding.jobs.db.insertJobs
import no.nav.syfo.sykmelding.jobs.db.resetJobs
import no.nav.syfo.sykmelding.jobs.db.updateJob
import no.nav.syfo.sykmelding.jobs.model.JOBNAME
import no.nav.syfo.sykmelding.jobs.model.JOBSTATUS
import no.nav.syfo.sykmelding.jobs.model.Job

class SendtSykmeldingService(
    private val databaseInterface: DatabaseInterface,
) {

    fun getNextJob(): Job? {
        return databaseInterface.getNextJob()
    }

    fun finishJob(job: Job) {
        databaseInterface.updateJob(job)
    }

    fun upsertSendtSykmelding(receivedSykmelding: ReceivedSykmelding) {
        databaseInterface.upsertSendtSykmelding(receivedSykmelding)
    }

    fun insertSendtSykmeldingHistory(sendtSykmeldingHistory: SendtSykmeldingHistory) {
        databaseInterface.insertSendtSykmeldingHistory(sendtSykmeldingHistory)
    }

    fun createJobs(receivedSykmelding: ReceivedSykmelding) {
        val sendSykmeldingJob =
            Job(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                status = JOBSTATUS.NEW,
                updated = OffsetDateTime.now(),
                name = JOBNAME.SENDT_SYKMELDING
            )
        log.info("Creating jobs:\n$sendSykmeldingJob")
        databaseInterface.insertJobs(listOf(sendSykmeldingJob))
    }

    fun getReceivedSykmelding(sykmeldingId: String): ReceivedSykmelding? {
        return databaseInterface.getSykmelding(sykmeldingId)
    }

    fun getReceivedSykmeldingWithTimestamp(sykmeldingId: String): ReceivedSykmeldingWithTimestamp? {
        return databaseInterface.getSykmeldingWithTimestamp(sykmeldingId)
    }

    fun getAllReceivedSykmeldingHistory(): List<SendtSykmeldingHistory> {
        return databaseInterface.geAlltSendtSykmeldingHistory()
    }

    fun getSykmeldingHistory(sykmeldingId: String): List<SendtSykmeldingHistory> {
        return databaseInterface.getSendtSykmeldingHistory(sykmeldingId)
    }

    fun resetHangingJobs() {
        val numbers = databaseInterface.resetJobs()
        if (numbers > 0) {
            log.info("Reset $numbers jobs FROM IN_PROGRESS to NEW")
        }
    }
}
