package no.nav.syfo.syfosmregister.sykmelding.model

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>
)
