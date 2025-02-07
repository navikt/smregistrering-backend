package no.nav.syfo.sykmelding

import no.nav.syfo.kafka.MigrationObject
import no.nav.syfo.kafka.SendtSykmeldingHistorySykDig
import no.nav.syfo.persistering.db.ManuellOppgaveDAO

class MigrationService(
    private val sykmeldingService: SendtSykmeldingService,
    private val manuellOppgaveDAO: ManuellOppgaveDAO
) {

    fun getAllMigrationObjects(): List<MigrationObject> {
        val alleOppgaver = manuellOppgaveDAO.hentAlleManuellOppgaverSykDig()
        val sykmeldingHistory = sykmeldingService.getAllReceivedSykmeldingHistory()
        val sykmelding = sykmeldingService.getAllReceivedSykmeldingWithTimestamp()
        val migrationObjectsMap = mutableMapOf<String, MigrationObject>()

        alleOppgaver.forEach { oppgave ->
            val existingObject = migrationObjectsMap[oppgave.sykmeldingId]
            if (existingObject == null) {
                migrationObjectsMap[oppgave.sykmeldingId] =
                    MigrationObject(
                        sykmeldingId = oppgave.sykmeldingId,
                        manuellOppgave = mutableListOf(oppgave),
                        sendtSykmeldingHistory = mutableListOf()
                    )
            } else {
                existingObject.manuellOppgave.add(oppgave)
            }
        }

        sykmeldingHistory.forEach { history ->
            val existingObject = migrationObjectsMap[history.sykmeldingId]
            if (existingObject == null) {
                migrationObjectsMap[history.sykmeldingId] =
                    MigrationObject(
                        sykmeldingId = history.sykmeldingId,
                        manuellOppgave = mutableListOf(),
                        sendtSykmeldingHistory = mutableListOf(history.mapToSykDig())
                    )
            } else {
                existingObject.sendtSykmeldingHistory?.add(history.mapToSykDig())
            }
        }

        sykmelding.forEach { sm ->
            val existingObject = migrationObjectsMap[sm.receivedSykmelding.sykmelding.id]
            if (existingObject == null) {
                migrationObjectsMap[sm.receivedSykmelding.sykmelding.id] =
                    MigrationObject(
                        sykmeldingId = sm.receivedSykmelding.sykmelding.id,
                        manuellOppgave = mutableListOf(),
                        sendtSykmeldingHistory = mutableListOf()
                    )
            } else if (existingObject.sendtSykmeldingHistory.isNullOrEmpty()) {
                migrationObjectsMap[sm.receivedSykmelding.sykmelding.id] =
                    MigrationObject(
                        sykmeldingId = sm.receivedSykmelding.sykmelding.id,
                        manuellOppgave = existingObject.manuellOppgave,
                        sendtSykmeldingHistory =
                            mutableListOf(
                                SendtSykmeldingHistorySykDig(
                                    id = sm.receivedSykmelding.sykmelding.id,
                                    sykmeldingId = sm.receivedSykmelding.sykmelding.id,
                                    ferdigstiltAv =
                                        existingObject.manuellOppgave.firstOrNull()?.ferdigstiltAv,
                                    datoFerdigstilt =
                                        existingObject.manuellOppgave
                                            .firstOrNull()
                                            ?.datoFerdigstilt,
                                    timestamp = sm.timestamp,
                                    receivedSykmelding = sm.receivedSykmelding
                                )
                            )
                    )
            }
        }

        val migrationObjects = migrationObjectsMap.values.toList()
        return migrationObjects
    }
}
