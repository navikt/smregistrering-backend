package no.nav.syfo.sykmelding

import no.nav.syfo.aksessering.db.oppdaterOppgave
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.KafkaProducers
import no.nav.syfo.log
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingJobRunner(
    private val applicationState: ApplicationState,
    private val kafkaSmregMigrationProducer: KafkaProducers.KafkaSmregMigrationProducer,
    private val migrationService: MigrationService
) {
    suspend fun startJobRunner() {
        while (applicationState.ready) {
            try {
                val migrationObject = migrationService.getMigrationObject()

                if (migrationObject == null) {
                    log.info("No more migration objects found. Stopping job runner.")
                    return
                }

                kafkaSmregMigrationProducer.producer
                    .send(
                        ProducerRecord(
                            kafkaSmregMigrationProducer.sm2013AutomaticHandlingTopic,
                            migrationObject.sykmeldingId,
                            migrationObject
                        )
                    )
                    .get()

                migrationService.oppdaterOppgave(migrationObject.sykmeldingId)
                log.info(
                    "publiserte oppgave på migreringstopic med sykmelding med id ${migrationObject.sykmeldingId}"
                )
            } catch (ex: Exception) {
                log.error("Error running Kafka producer: ${ex.message}", ex)
                applicationState.ready = false
                applicationState.alive = false
                return
            }
        }
    }
}
