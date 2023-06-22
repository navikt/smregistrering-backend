package no.nav.syfo.util

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.WhitelistedRuleHit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RuleInfoExtTest {
    @Test
    internal fun `Returnerer true hvis alle regler finnes i WhitelistedRuleHits`() {
        val ruleHits: List<RuleInfo> =
            listOf(
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName =
                        WhitelistedRuleHit
                            .TILBAKEDATERT_INNTIL_8_DAGER_UTEN_KONTAKTDATO_OG_BEGRUNNELSE
                            .toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName =
                        WhitelistedRuleHit.TILBAKEDATERT_MED_BEGRUNNELSE_FORSTE_SYKMELDING
                            .toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName =
                        WhitelistedRuleHit.TILBAKEDATERT_MED_BEGRUNNELSE_FORLENGELSE.toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName = WhitelistedRuleHit.PASIENTEN_HAR_KODE_6.toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
            )

        assertEquals(true, ruleHits.isWhitelisted())
    }

    @Test
    internal fun `Returnerer false hvis en av reglene ikke finnes i WhitelistedRuleHits`() {
        val ruleHits: List<RuleInfo> =
            listOf(
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName = "unknown rulename",
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName =
                        WhitelistedRuleHit.TILBAKEDATERT_MED_BEGRUNNELSE_FORSTE_SYKMELDING
                            .toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
                RuleInfo(
                    messageForSender = "",
                    messageForUser = "",
                    ruleName =
                        WhitelistedRuleHit.TILBAKEDATERT_MED_BEGRUNNELSE_FORLENGELSE.toString(),
                    ruleStatus = Status.MANUAL_PROCESSING,
                ),
            )

        assertEquals(false, ruleHits.isWhitelisted())
    }
}
