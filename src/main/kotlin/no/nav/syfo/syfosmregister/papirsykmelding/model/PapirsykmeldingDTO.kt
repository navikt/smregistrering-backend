package no.nav.syfo.syfosmregister.papirsykmelding.model

import no.nav.syfo.model.Sykmelding
import java.time.OffsetDateTime

data class PapirsykmeldingDTO(
    val pasientFnr: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)
