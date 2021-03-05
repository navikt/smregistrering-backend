package no.nav.syfo.persistering.api

import java.time.LocalDate
import no.nav.syfo.model.Periode
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult

fun validate(smRegistreringManuell: SmRegistreringManuell) {

    if (smRegistreringManuell.perioder.isEmpty()) {
        val validationResult = ValidationResult(
            status = Status.MANUAL_PROCESSING,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "periodeValidation",
                    messageForSender = "Sykmeldingen må ha minst én periode oppgitt for å være gyldig",
                    messageForUser = "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )
        )
        throw ValidationException(validationResult)
    } else if (harOverlappendePerioder(smRegistreringManuell.perioder)) {
        val validationResult = ValidationResult(
            status = Status.MANUAL_PROCESSING,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "overlappendePeriodeValidation",
                    messageForSender = "Sykmeldingen har overlappende perioder",
                    messageForUser = "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )
        )
        throw ValidationException(validationResult)
    } else if (harUlovligKombinasjonMedReisetilskudd(smRegistreringManuell.perioder)) {
        val validationResult = ValidationResult(
            status = Status.MANUAL_PROCESSING,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "reisetilskuddValidation",
                    messageForSender = "Sykmeldingen inneholder periode som kombinerer reisetilskudd med annen sykmeldingstype",
                    messageForUser = "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )
        )
        throw ValidationException(validationResult)
    } else if (erFremtidigDato(smRegistreringManuell.behandletDato)) {
        val validationResult = ValidationResult(
            status = Status.MANUAL_PROCESSING,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "behandletDatoValidation",
                    messageForSender = "Behandletdato kan ikke være frem i tid.",
                    messageForUser = "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            )
        )
        throw ValidationException(validationResult)
    }
}

fun harOverlappendePerioder(perioder: List<Periode>): Boolean {
    return perioder.any { periodA ->
        perioder
            .filter { periodB -> periodB != periodA }
            .any { periodB ->
                periodA.fom in periodB.range() || periodA.tom in periodB.range()
            }
    }
}

fun harUlovligKombinasjonMedReisetilskudd(perioder: List<Periode>): Boolean {
    perioder.forEach {
        if (it.reisetilskudd && (it.aktivitetIkkeMulig != null || it.gradert != null || it.avventendeInnspillTilArbeidsgiver != null || it.behandlingsdager != null)) {
            return true
        }
    }
    return false
}

fun Periode.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

fun erFremtidigDato(dato: LocalDate): Boolean {
    return dato.isAfter(LocalDate.now())
}
