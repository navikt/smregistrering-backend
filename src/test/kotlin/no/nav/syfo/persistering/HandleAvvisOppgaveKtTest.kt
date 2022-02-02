package no.nav.syfo.persistering

import no.nav.syfo.service.Veileder
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.time.LocalDateTime

internal class HandleAvvisOppgaveKtTest {
    private val opprinneligBeskrivelse = "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
        "Viktig beskrivelse!\n" +
        "\n" +
        "Manuell registrering av sykmelding mottatt på papir"
    private val veileder = Veileder("Z999999")
    private val enhet = "0101"
    private val timestamp = LocalDateTime.of(2022, 2, 4, 11, 23)

    @Test
    fun lagOppgavebeskrivelseLagerRiktigBeskrivelseMedAvvisningsarsak() {
        val avvisSykmeldingReason = "Feil avventende periode"

        val oppdatertBeskrivelse = lagOppgavebeskrivelse(avvisSykmeldingReason, opprinneligBeskrivelse, veileder, enhet, timestamp)

        oppdatertBeskrivelse shouldBeEqualTo "--- 04.02.2022 11:23 Z999999, 0101 ---\n" +
            "Avvist papirsykmelding med årsak: Feil avventende periode\n" +
            "\n" +
            "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
            "Viktig beskrivelse!\n" +
            "\n" +
            "Manuell registrering av sykmelding mottatt på papir"
    }

    @Test
    fun lagOppgavebeskrivelseLagerRiktigBeskrivelseUtenAvvisningsarsak() {
        val avvisSykmeldingReason = null

        val oppdatertBeskrivelse = lagOppgavebeskrivelse(avvisSykmeldingReason, opprinneligBeskrivelse, veileder, enhet, timestamp)

        oppdatertBeskrivelse shouldBeEqualTo "--- 04.02.2022 11:23 Z999999, 0101 ---\n" +
            "Avvist papirsykmelding uten oppgitt årsak.\n" +
            "\n" +
            "--- 02.02.2022 10:14 F_Z990098 E_Z990098 (z990098, 2820) ---\n" +
            "Viktig beskrivelse!\n" +
            "\n" +
            "Manuell registrering av sykmelding mottatt på papir"
    }
}
