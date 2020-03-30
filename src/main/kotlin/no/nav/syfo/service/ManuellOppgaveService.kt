package no.nav.syfo.service

import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.persistering.db.ferdigstillSmRegistering

class ManuellOppgaveService(private val database: DatabaseInterface) {

    fun hentManuellOppgaver(oppgaveId: Int): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaver(oppgaveId)

    fun ferdigstillSmRegistering(oppgaveId: Int): Int =
        database.ferdigstillSmRegistering(oppgaveId)
}
