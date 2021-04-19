package no.nav.syfo.util

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.WhitelistedRuleHit

fun List<RuleInfo>.isWhitelisted(): Boolean {
    return this.all { (ruleName) ->
        val isWhiteListed = enumValues<WhitelistedRuleHit>().any { enumValue ->
            enumValue.name == ruleName
        }
        isWhiteListed
    }
}
