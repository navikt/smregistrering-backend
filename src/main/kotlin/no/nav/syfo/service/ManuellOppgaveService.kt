package no.nav.syfo.service

import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.Utfall
import no.nav.syfo.persistering.db.ferdigstillManuellOppgave
import no.nav.syfo.persistering.db.gjenaapneManuellOppgave

class ManuellOppgaveService(private val database: DatabaseInterface) {

    fun hentManuellOppgaver(oppgaveId: Int, ferdigstilt: Boolean = false): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaver(oppgaveId, ferdigstilt)

    fun ferdigstillManuellOppgave(oppgaveId: Int, utfall: Utfall, ferdigstiltAv: String): Int =
        database.ferdigstillManuellOppgave(oppgaveId = oppgaveId, utfall = utfall.toString(), ferdigstiltAv = ferdigstiltAv)

    fun gjenaapneManuellOppgave(oppgaveId: Int): Int =
        database.gjenaapneManuellOppgave(oppgaveId = oppgaveId)
}
