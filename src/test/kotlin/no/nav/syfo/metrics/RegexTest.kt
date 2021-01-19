package no.nav.syfo.metrics

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class RegexTest {

    @Test
    fun testRegex() {
        val uri = "https://localhost.no/api/v1/oppgave/319200389/send"
        val newPath = REGEX.replace(uri, ":id")
        newPath shouldBeEqualTo "https://localhost.no/api/v1/oppgave/:id/send"
    }
}
