package no.nav.syfo.sykmelding

import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.sykmelding.jobs.model.JOBNAME
import no.nav.syfo.sykmelding.jobs.model.JOBSTATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingJobRunner(
    private val applicationState: ApplicationState,
    private val sendtSykmeldingService: SendtSykmeldingService,
    private val receivedSykmeldingKafkaProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
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
            JOBNAME.SENDT_TO_SYFOSERVICE -> log.info("Sender ikke lenger til syfoservice")
            JOBNAME.SENDT_SYKMELDING -> sendSykmelding(nextJob)
        }
        sendtSykmeldingService.finishJob(
            nextJob.copy(updated = OffsetDateTime.now(), status = JOBSTATUS.DONE)
        )
    }

    private fun sendSykmelding(job: Job) {
        try {
            val receivedSykmelding = sendtSykmeldingService.getReceivedSykmelding(job.sykmeldingId)
            receivedSykmeldingKafkaProducer.producer
                .send(
                    ProducerRecord(
                        receivedSykmeldingKafkaProducer.sm2013AutomaticHandlingTopic,
                        job.sykmeldingId,
                        receivedSykmelding,
                    ),
                )
                .get()
        } catch (ex: Exception) {
            log.error("Error producing sykmelding to kafka for job $job}")
            throw ex
        }
    }
}
