package no.nav.syfo.sykmelding

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toReceivedSykmeldingWithValidation
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
                handleJobIteration()
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

    @WithSpan()
    private fun handleJobIteration() {
        sendtSykmeldingService.resetHangingJobs()
        val nextJob = sendtSykmeldingService.getNextJob()
        if (nextJob != null) {
            runJob(nextJob = nextJob)
        }
    }

    private fun runJob(nextJob: Job) {
        log.info("Running job $nextJob")
        when (nextJob.name) {
            JOBNAME.SENDT_TO_SYFOSERVICE -> log.info("Sender ikke lenger til syfoservice")
            JOBNAME.SENDT_SYKMELDING -> sendSykmelding(nextJob)
        }
        sendtSykmeldingService.finishJob(
            nextJob.copy(updated = OffsetDateTime.now(), status = JOBSTATUS.DONE),
        )
    }

    private fun sendSykmelding(job: Job) {
        try {
            val receivedSykmelding = sendtSykmeldingService.getReceivedSykmelding(job.sykmeldingId)
            if (receivedSykmelding == null) {
                log.error("Received sykmelding is null for job $job")
                throw RuntimeException("Received sykmelding is null for job $job")
            }
            receivedSykmeldingKafkaProducer.producer
                .send(
                    ProducerRecord(
                        receivedSykmeldingKafkaProducer.sm2013AutomaticHandlingTopic,
                        job.sykmeldingId,
                        receivedSykmelding.toReceivedSykmeldingWithValidation(
                            ValidationResult(
                                Status.OK,
                                emptyList(),
                            ),
                        ),
                    ),
                )
                .get()
        } catch (ex: Exception) {
            log.error("Error producing sykmelding to kafka for job $job}")
            throw ex
        }
    }
}
