package no.nav.syfo.model

import java.util.Date

data class Samhandler(
    val samh_id: String,
    val navn: String,
    val samh_type_kode: String,
    val behandling_utfall_kode: String,
    val unntatt_veiledning: String,
    val godkjent_manuell_krav: String,
    val ikke_godkjent_for_refusjon: String,
    val godkjent_egenandel_refusjon: String,
    val godkjent_for_fil: String,
    val endringslogg_tidspunkt_siste: Date?,
    val samh_ident: List<Samhandlerident>,
    val samh_praksis: List<SamhandlerPraksis>
)

data class SamhandlerPraksis(
    val org_id: String?,
    val laerer: String?,
    val lege_i_spesialisering: String?,
    val tidspunkt_resync_periode: Date?,
    val tidspunkt_registrert: Date?,
    val samh_praksis_status_kode: String,
    val telefonnr: String?,
    val arbeids_kommune_nr: String?,
    val arbeids_postnr: String?,
    val arbeids_adresse_linje_1: String?,
    val arbeids_adresse_linje_2: String?,
    val arbeids_adresse_linje_3: String?,
    val arbeids_adresse_linje_4: String?,
    val arbeids_adresse_linje_5: String?,
    val her_id: String?,
    val post_adresse_linje_1: String?,
    val post_adresse_linje_2: String?,
    val post_adresse_linje_3: String?,
    val post_adresse_linje_4: String?,
    val post_adresse_linje_5: String?,
    val post_kommune_nr: String?,
    val post_postnr: String?,
    val tss_ident: String,
    val navn: String?,
    val ident: String?,
    val samh_praksis_type_kode: String?,
    val samh_id: String,
    val samh_praksis_id: String,
    val samh_praksis_periode: List<SamhandlerPeriode>
)

data class SamhandlerPeriode(
    val slettet: String,
    val gyldig_fra: Date,
    val gyldig_til: Date?,
    val samh_praksis_id: String,
    val samh_praksis_periode_id: String
)

data class Samhandlerident(
    val samh_id: String?,
    val samh_ident_id: String?,
    val ident: String?,
    val ident_type_kode: String?,
    val aktiv_ident: String?
)

data class SamhandlerPraksisMatch(val samhandlerPraksis: SamhandlerPraksis, val percentageMatch: Double)
