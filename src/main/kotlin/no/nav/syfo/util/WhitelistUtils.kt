package no.nav.syfo.util

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.WhitelistedRuleHits

fun allRulesWhitelisted(ruleHits: List<RuleInfo>): Boolean {
    return ruleHits.all { (ruleName) ->
        val isWhiteListed = enumValues<WhitelistedRuleHits>().any { enumValue ->
            enumValue.name == ruleName
        }
        isWhiteListed
    }
}
