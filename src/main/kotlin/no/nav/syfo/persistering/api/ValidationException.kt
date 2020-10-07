package no.nav.syfo.persistering.api

import no.nav.syfo.model.ValidationResult

class ValidationException(val validationResult: ValidationResult) : Exception()
