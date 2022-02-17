package no.nav.syfo.service

import no.nav.syfo.aksessering.db.hentManuellOppgaveForSykmelding
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Utfall
import no.nav.syfo.persistering.db.ferdigstillSmRegistering
import no.nav.syfo.sykmelding.db.getSykmelding

class ManuellOppgaveService(private val database: DatabaseInterface) {

    fun hentManuellOppgaver(oppgaveId: Int): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaver(oppgaveId)

    fun ferdigstillSmRegistering(oppgaveId: Int, utfall: Utfall, ferdigstiltAv: String, avvisningsgrunn: String? = null): Int =
        database.ferdigstillSmRegistering(
            oppgaveId = oppgaveId,
            utfall = utfall.toString(),
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn
        )

    fun hentFerdigstiltManuellOppgave(sykmeldingId: String): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaveForSykmelding(sykmeldingId, true)

    fun hentSykmelding(sykmeldingId: String): ReceivedSykmelding? =
        database.getSykmelding(sykmeldingId)
}
