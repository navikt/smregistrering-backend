package no.nav.syfo.sykmelding

import kotlinx.coroutines.delay
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
                    log.error("Migration object is null. Stopping producer.")
                    applicationState.ready = false
                    break
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
            } catch (ex: Exception) {
                log.error("Error running kafka producer: ${ex.message}", ex)
                applicationState.ready = false
                applicationState.alive = false
                break
            }
            delay(3_000)
        }
    }
}
