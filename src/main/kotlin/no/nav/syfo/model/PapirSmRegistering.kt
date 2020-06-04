package no.nav.syfo.model

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

data class PapirSmRegistering(
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: LocalDateTime?,
    val sykmeldingId: String,
    val syketilfelleStartDato: LocalDate?,
    val arbeidsgiver: Arbeidsgiver?,
    val medisinskVurdering: MedisinskVurdering?,
    val skjermesForPasient: Boolean?,
    val perioder: List<Periode>?,
    val prognose: Prognose?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>?,
    val tiltakNAV: String?,
    val tiltakArbeidsplassen: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNAV?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasient?,
    val behandletTidspunkt: LocalDate?,
    val behandler: Behandler?
)

fun PapirSmRegistering.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
