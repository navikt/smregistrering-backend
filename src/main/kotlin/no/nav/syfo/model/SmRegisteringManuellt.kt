package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

data class SmRegisteringManuellt(
    val pasientFnr: String,
    val sykmelderFnr: String,
    val perioder: List<Periode>,
    val medisinskVurdering: MedisinskVurdering,
    val syketilfelleStartDato: LocalDate,
    val arbeidsgiver: Arbeidsgiver,
    val behandletDato: LocalDateTime,
    val skjermesForPasient: Boolean
)
