package no.nav.syfo.model

enum class WhitelistedRuleHit {
    INNTIL_8_DAGER,
    INNTIL_30_DAGER,
    INNTIL_30_DAGER_MED_BEGRUNNELSE,
    OVER_30_DAGER,
    INNTIL_1_MAANED,
    INNTIL_1_MAANED_MED_BEGRUNNELSE,
    OVER_1_MAANED,
    TILBAKEDATERING_OVER_4_DAGER,
    FREMDATERT,
    PASIENTEN_HAR_KODE_6,
}

enum class RuleHitCustomError {
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR,
    BEHANDLER_SUSPENDERT,
}
