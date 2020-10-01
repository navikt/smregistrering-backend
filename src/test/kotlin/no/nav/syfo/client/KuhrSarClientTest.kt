package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.model.Samhandler
import no.nav.syfo.model.SamhandlerPraksis
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.junit.Test

class KuhrSarClientTest {
    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val loggingMeta = mockk<LoggingMeta>(relaxed = true)
    val samhandlerPraksis = mockk<SamhandlerPraksis>(relaxed = true)
    @Test
    fun `Finner en samhandler når det bare er inaktivte samhandlere`() {
        val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(
            KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_response_inaktive.json").readBytes()
                .toString(Charsets.UTF_8)
        )

        val match = samhandlerMatchingPaaOrganisjonsNavn(samhandlerMedNavn, "Testlegesenteret")

        match?.samhandlerPraksis?.navn shouldEqual "Testlegesenteret - org nr"
    }

    @Test
    fun findSamhandlerKiropraktorActive() {
        every { samhandlerPraksis.tss_ident } returns "123456789101112"
        every { samhandlerPraksis.samh_praksis_status_kode } returns "aktiv"
        val samhandlere = listOf(Samhandler(
                "1",
                "samhandlernavn",
                "KI",
                "",
                "",
                "", "", "", "", null, emptyList(), listOf(samhandlerPraksis)
        ))
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(samhandlere, loggingMeta)
        samhandlerPraksisMatch?.samhandlerPraksis?.tss_ident shouldEqual "123456789101112"
    }

    @Test
    fun findSamhandlerKiropraktorInaktivOgActive() {
        val samhandlerPraksis2 = mockk<SamhandlerPraksis>(relaxed = true)
        every { samhandlerPraksis2.tss_ident } returns "2"
        every { samhandlerPraksis2.samh_praksis_status_kode } returns "inaktiv"
        every { samhandlerPraksis.tss_ident } returns "123456789101112"
        every { samhandlerPraksis.samh_praksis_status_kode } returns "aktiv"
        val samhandlere = getSamhandler("KI", samhandlerPraksis2, samhandlerPraksis)
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(samhandlere, loggingMeta)
        samhandlerPraksisMatch?.samhandlerPraksis?.tss_ident shouldEqual "123456789101112"
    }

    private fun getSamhandler(type: String, vararg samhandlerPraksis: SamhandlerPraksis): MutableList<Samhandler> {
        val samhandlere = mutableListOf(Samhandler(
                "1",
                "samhandlernavn",
                type,
                "",
                "",
                "", "", "", "", null, emptyList(), samhandlerPraksis.toList())
        )
        return samhandlere
    }

    @Test
    fun findFirstActiveSamhandlerPraksis() {
        val samhandlerPraksis2 = mockk<SamhandlerPraksis>(relaxed = true)
        every { samhandlerPraksis2.tss_ident } returns "2"
        every { samhandlerPraksis2.samh_praksis_status_kode } returns "aktiv"
        val samhandlere = getSamhandler("LE", samhandlerPraksis2)
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(samhandlere, loggingMeta)
        samhandlerPraksisMatch?.samhandlerPraksis?.tss_ident shouldEqual "2"
    }

    @Test
    fun findInactiveSamhandlerPraksis() {
        every { samhandlerPraksis.tss_ident } returns "1"
        every { samhandlerPraksis.samh_praksis_status_kode } returns "inaktiv"
        val samhandlere = getSamhandler("ANY", samhandlerPraksis)
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(samhandlere, loggingMeta)
        samhandlerPraksisMatch?.samhandlerPraksis?.tss_ident shouldEqual "1"
    }
}
