package no.nav.syfo.persistering.api

import no.nav.syfo.model.SmRegistreringManuell

fun validate(smRegistreringManuell: SmRegistreringManuell) {

    if (smRegistreringManuell.prognose?.erIArbeid != null && smRegistreringManuell.prognose.erIkkeIArbeid != null) {
        throw ValidationException("Sykmeldingen kan ikke ha både 5.2 og 5.3 fylt ut samtidig")
    }
}
