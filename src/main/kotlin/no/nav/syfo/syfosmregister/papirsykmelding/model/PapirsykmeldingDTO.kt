package no.nav.syfo.syfosmregister.papirsykmelding.model

import java.time.LocalDateTime
import no.nav.syfo.model.Sykmelding

data class PapirsykmeldingDTO(
    val pasientFnr: String,
    val mottattTidspunkt: LocalDateTime,
    val sykmelding: Sykmelding,
)
