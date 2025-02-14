package no.nav.syfo.persistering.db

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.syfo.aksessering.db.getUmigrertManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaveForSykmelding
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.aksessering.db.oppdaterOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.ManuellOppgaveDTOSykDig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Utfall
import no.nav.syfo.sykmelding.db.getSykmelding
import kotlin.jvm.Throws

class ManuellOppgaveDAO(private val database: DatabaseInterface) {

    @WithSpan
    fun hentManuellOppgaver(
        @SpanAttribute oppgaveId: Int,
        ferdigstilt: Boolean = false
    ): List<ManuellOppgaveDTO> = database.hentManuellOppgaver(oppgaveId, ferdigstilt)

    fun getUmigrertManuellOppgave(): ManuellOppgaveDTOSykDig? {
        val oppgave = database.getUmigrertManuellOppgave()
        if (oppgave == null) {
            log.warn("Ingen umigrert oppgave funnet")
            return null
        }
        return oppgave
    }
    fun updateToMigrert(sykmeldingId: String) {
        val oppdatert = database.oppdaterOppgave(sykmeldingId)
        if (oppdatert > 0) {
            log.info(
                "Hentet og oppdatert migrert oppgave med sykmeldingId: ${sykmeldingId}"
            )
        } else {
            log.error("Ingen rader ble oppdatert for sykmeldingId: ${sykmeldingId}")
            throw Exception("Ingen rader ble oppdatert for sykmeldingId: ${sykmeldingId}")
        }
    }

    fun ferdigstillSmRegistering(
        sykmeldingId: String,
        utfall: Utfall,
        ferdigstiltAv: String,
        avvisningsgrunn: String? = null
    ): Int =
        database.ferdigstillSmRegistering(
            sykmeldingId = sykmeldingId,
            utfall = utfall.toString(),
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn,
        )

    fun hentFerdigstiltManuellOppgave(sykmeldingId: String): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaveForSykmelding(sykmeldingId)

    fun hentSykmelding(sykmeldingId: String): ReceivedSykmelding? =
        database.getSykmelding(sykmeldingId)
}
