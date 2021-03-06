package no.nav.syfo.sykmelding

import java.io.StringReader
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.sykmelding.jobs.model.JOB_NAME
import no.nav.syfo.sykmelding.jobs.model.JOB_STATUS
import no.nav.syfo.sykmelding.jobs.model.Job
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingJobRunner(
    private val applicationState: ApplicationState,
    private val sykmeldingJobService: SykmeldingJobService,
    private val receivedSykmeldingKafkaProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    private val syfoserviceKafkaProducer: KafkaProducers.KafkaSyfoserviceProducer
) {
    suspend fun startJobRunner() {
        while (applicationState.ready) {
            try {
                sykmeldingJobService.resetHangingJobs()
                val nextJob = sykmeldingJobService.getNextJob()
                if (nextJob != null) {
                    runJob(nextJob = nextJob)
                }
            } catch (ex: Exception) {
                log.error("Could not process jobs", ex)
            }
            delay(3_000)
        }
    }

    private fun runJob(nextJob: Job) {
        log.info("Running job $nextJob")
        when (nextJob.name) {
            JOB_NAME.SENDT_TO_SYFOSERVICE -> sendToSyfoService(nextJob)
            JOB_NAME.SENDT_SYKMELDING -> sendSykmelding(nextJob)
        }
        sykmeldingJobService.finishJob(nextJob.copy(updated = OffsetDateTime.now(), status = JOB_STATUS.DONE))
    }

    private fun sendSykmelding(job: Job) {
        try {
            val receivedSykmelding = sykmeldingJobService.getReceivedSykmelding(job.sykmeldingId)
            receivedSykmeldingKafkaProducer.producer.send(
                    ProducerRecord(receivedSykmeldingKafkaProducer.sm2013AutomaticHandlingTopic, job.sykmeldingId,
                            receivedSykmelding)).get()
        } catch (ex: Exception) {
            log.error("Error producing sykmelding to kafka for job $job}")
            throw ex
        }
    }

    private fun sendToSyfoService(job: Job) {
        try {

            val receivedSykmelding = sykmeldingJobService.getReceivedSykmelding(job.sykmeldingId)
            if (receivedSykmelding != null) {
                val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat
                notifySyfoService(
                        syfoserviceKafkaProducer = syfoserviceKafkaProducer,
                        sykmeldingId = job.sykmeldingId,
                        healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                )
            } else {
                throw Exception("Could not find sykmelding ${job.sykmeldingId} in database}")
            }
        } catch (ex: Exception) {
            log.error("Error producing sykmelding to kafka for job $job")
            throw ex
        }
    }
}
