package no.nav.syfo.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegexTest {

    @Test
    fun testRegex() {
        val uri = "https://localhost.no/api/v1/oppgave/319200389/send"
        val newPath = REGEX.replace(uri, ":id")
        assertEquals("https://localhost.no/api/v1/oppgave/:id/send", newPath)
    }
}
