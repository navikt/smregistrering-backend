package no.nav.syfo.model

import java.time.LocalDate

data class SmRegistreringManuell(
    val pasientFnr: String,
    val sykmelderFnr: String,
    val perioder: List<Periode>,
    val medisinskVurdering: MedisinskVurdering,
    val syketilfelleStartDato: LocalDate,
    val arbeidsgiver: Arbeidsgiver,
    val behandletDato: LocalDate,
    val skjermesForPasient: Boolean,
    val behandler: Behandler,
    val kontaktMedPasient: KontaktMedPasient,
    val prognose: Prognose?,
    val meldingTilNAV: MeldingTilNAV?,
    val meldingTilArbeidsgiver: String?,
    val tiltakNAV: String?,
    val tiltakArbeidsplassen: String?,
    val andreTiltak: String?,
    val utdypendeOpplysninger: Map<String, Map<String, String>>?,
    val navnFastlege: String?
)
