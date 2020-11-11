package no.nav.syfo.util

import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

fun toPGObject(obj: Any) = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(obj)
}
