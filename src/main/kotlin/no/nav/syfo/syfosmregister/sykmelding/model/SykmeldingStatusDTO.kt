package no.nav.syfo.syfosmregister.sykmelding.model

import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import java.time.OffsetDateTime

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
