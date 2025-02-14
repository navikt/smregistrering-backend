package no.nav.syfo.sykmelding

import no.nav.syfo.aksessering.db.oppdaterOppgave
import no.nav.syfo.kafka.MigrationObject
import no.nav.syfo.kafka.SendtSykmeldingHistorySykDig
import no.nav.syfo.log
import no.nav.syfo.persistering.db.ManuellOppgaveDAO

class MigrationService(
    private val sykmeldingService: SendtSykmeldingService,
    private val manuellOppgaveDAO: ManuellOppgaveDAO
) {

    fun getMigrationObject(): MigrationObject? {
        val manuellOppgave = manuellOppgaveDAO.getUmigrertManuellOppgave()
        if (manuellOppgave == null) {
            log.info("ingen flere umigrerte oppgaver")
            return null
        }
        val sykmeldingHistory = sykmeldingService.getSykmeldingHistory(manuellOppgave.sykmeldingId)
        if (sykmeldingHistory.isNotEmpty()) {
            return MigrationObject(
                manuellOppgave.sykmeldingId,
                manuellOppgave,
                sykmeldingHistory.map { it.mapToSykDig() }
            )
        }
        val sykmelding =
            sykmeldingService.getReceivedSykmeldingWithTimestamp(manuellOppgave.sykmeldingId)
        if (sykmelding != null) {
            return MigrationObject(
                manuellOppgave.sykmeldingId,
                manuellOppgave,
                listOf(
                    SendtSykmeldingHistorySykDig(
                        manuellOppgave.sykmeldingId,
                        manuellOppgave.ferdigstiltAv,
                        manuellOppgave.datoFerdigstilt,
                        sykmelding.timestamp,
                        sykmelding.receivedSykmelding
                    )
                )
            )
        }
        return MigrationObject(manuellOppgave.sykmeldingId, manuellOppgave, null)
    }

    fun oppdaterOppgave(sykmeldingId: String){
        manuellOppgaveDAO.updateToMigrert(sykmeldingId)
    }
}
