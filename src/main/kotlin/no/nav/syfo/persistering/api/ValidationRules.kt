package no.nav.syfo.persistering.api

import java.time.LocalDate
import no.nav.syfo.model.Periode
import no.nav.syfo.model.RuleHitCustomError
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.ValidationResult

fun checkValidState(
    smRegistreringManuell: SmRegistreringManuell,
    sykmelder: Sykmelder,
    validationResult: ValidationResult
) {

    when {
        smRegistreringManuell.perioder.isEmpty() -> {
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
        }
        harOverlappendePerioder(smRegistreringManuell.perioder) -> {
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
        }
        harUlovligKombinasjonMedReisetilskudd(smRegistreringManuell.perioder) -> {
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
        }
        erFremtidigDato(smRegistreringManuell.behandletDato) -> {
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
        studentBehandlerUtenAutorisasjon(validationResult, sykmelder) -> {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name,
                        messageForSender = "Studenter har ikke lov til å skrive sykmelding. Sykmledingen må avvises.",
                        messageForUser = "Studenter har ikke lov til å skrive sykmelding.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )
            throw ValidationException(validationResult)
        }
        suspendertBehandler(validationResult) -> {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = RuleHitCustomError.BEHANDLER_SUSPENDERT.name,
                        messageForSender = "Legen har mistet retten til å skrive sykmelding.",
                        messageForUser = "Legen har mistet retten til å skrive sykmelding.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )
            throw ValidationException(validationResult)
        }
    }
}

fun suspendertBehandler(validationResult: ValidationResult): Boolean {
    return validationResult.ruleHits.any {
        it.ruleName == RuleHitCustomError.BEHANDLER_SUSPENDERT.name
    }
}

fun studentBehandlerUtenAutorisasjon(validationResult: ValidationResult, sykmelder: Sykmelder): Boolean {
    val behandlerManglerAutorisasjon = validationResult.ruleHits.any {
        it.ruleName == RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name
    }

    val erStudent = sykmelder.godkjenninger?.any {
        it.autorisasjon?.aktiv == true && it.autorisasjon.oid == 7704 && it.autorisasjon.verdi == "3"
    }

    return behandlerManglerAutorisasjon && erStudent == true
}

fun harOverlappendePerioder(perioder: List<Periode>): Boolean {
    return harIdentiskePerioder(perioder) || perioder.any { periodA ->
        perioder
            .filter { periodB -> periodB != periodA }
            .any { periodB ->
                periodA.fom in periodB.range() || periodA.tom in periodB.range()
            }
    }
}

private fun harIdentiskePerioder(perioder: List<Periode>): Boolean {
    return perioder.distinct().count() != perioder.size
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
