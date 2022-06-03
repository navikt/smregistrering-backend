package no.nav.syfo.sykmelding

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException

class SykmeldingJobRunner(
    private val applicationState: ApplicationState,
    private val sendtSykmeldingService: SendtSykmeldingService,
    private val receivedSykmeldingKafkaProducer: KafkaProducers.KafkaRecievedSykmeldingProducer
) {
    suspend fun startJobRunner() {
        while (applicationState.ready) {
            try {
                sendtSykmeldingService.resetHangingJobs()
                val nextJob = sendtSykmeldingService.getNextJob()
                if (nextJob != null) {
                    runJob(nextJob = nextJob)
                }
            } catch (ex: Exception) {
                log.error("Could not process jobs", ex)

                if (ex is ExecutionException) {
                    log.error("Exception is ExecutionException, restarting..", ex.cause)
                    applicationState.ready = false
                    applicationState.alive = false
                }
            }
            delay(3_000)
        }
    }

    private fun runJob(nextJob: Job) {
        log.info("Running job $nextJob")
        when (nextJob.name) {
            JOB_NAME.SENDT_TO_SYFOSERVICE -> log.info("Sender ikke lenger til syfoservice")
            JOB_NAME.SENDT_SYKMELDING -> sendSykmelding(nextJob)
        }
        sendtSykmeldingService.finishJob(nextJob.copy(updated = OffsetDateTime.now(), status = JOB_STATUS.DONE))
    }

    private fun sendSykmelding(job: Job) {
        try {
            val receivedSykmelding = sendtSykmeldingService.getReceivedSykmelding(job.sykmeldingId)
            receivedSykmeldingKafkaProducer.producer.send(
                ProducerRecord(
                    receivedSykmeldingKafkaProducer.sm2013AutomaticHandlingTopic, job.sykmeldingId,
                    receivedSykmelding
                )
            ).get()
        } catch (ex: Exception) {
            log.error("Error producing sykmelding to kafka for job $job}")
            throw ex
        }
    }
}
