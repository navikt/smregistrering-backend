package no.nav.syfo.model

enum class WhitelistedRuleHit {
    TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING,
    TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE,
    TILBAKEDATERT_INNTIL_8_DAGER_UTEN_KONTAKTDATO_OG_BEGRUNNELSE,
    TILBAKEDATERT_FORLENGELSE_OVER_1_MND,
    TILBAKEDATERT_MED_BEGRUNNELSE_FORSTE_SYKMELDING,
    TILBAKEDATERT_MED_BEGRUNNELSE_FORLENGELSE,
    FREMDATERT,
    PASIENTEN_HAR_KODE_6,
}

enum class RuleHitCustomError {
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR,
    BEHANDLER_SUSPENDERT,
}
