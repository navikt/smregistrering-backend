package no.nav.syfo.syfosmregister.papirsykmelding.model

import java.time.OffsetDateTime
import no.nav.syfo.model.Sykmelding

data class PapirsykmeldingDTO(
    val pasientFnr: String,
    val pasientAktoerId: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)
