package no.nav.syfo.persistering.api

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult

fun validate(smRegistreringManuell: SmRegistreringManuell) {

    if (smRegistreringManuell.prognose?.erIArbeid != null && smRegistreringManuell.prognose.erIkkeIArbeid != null) {
        val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                        RuleInfo(
                                ruleName = "erIArbeidValidation",
                                messageForSender = "Sykmeldingen kan ikke ha både 5.2 og 5.3 fylt ut samtidig",
                                messageForUser = "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                ruleStatus = Status.MANUAL_PROCESSING
                        )
                )
        )
        throw ValidationException(validationResult)
    } else if (smRegistreringManuell.perioder.isEmpty()) {
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
}
