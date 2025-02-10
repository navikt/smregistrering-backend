package no.nav.syfo.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class PapirSmRegistering(
    val journalpostId: String,
    val oppgaveId: String?,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: OffsetDateTime?,
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
    val behandler: Behandler?,
)

data class PapirSmRegisteringSykDig(
    val journalpostId: String = "",
    val oppgaveId: String? = null,
    val fnr: String? = null,
    val aktorId: String? = null,
    val dokumentInfoId: String? = null,
    val datoOpprettet: OffsetDateTime? = null,
    val sykmeldingId: String = "",
    val syketilfelleStartDato: LocalDate? = null,
    val arbeidsgiver: ArbeidsgiverSykDig? = null,
    val medisinskVurdering: MedisinskVurdering? = null,
    val skjermesForPasient: Boolean? = null,
    val perioder: List<Periode>? = null,
    val prognose: PrognoseSykDig? = null,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>? = null,
    val tiltakNAV: String? = null,
    val tiltakArbeidsplassen: String? = null,
    val andreTiltak: String? = null,
    val meldingTilNAV: MeldingTilNAV? = null,
    val meldingTilArbeidsgiver: String? = null,
    val kontaktMedPasient: KontaktMedPasient? = null,
    val behandletTidspunkt: LocalDate? = null,
    val behandler: BehandlerSykDig? = null
)
