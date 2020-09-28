package no.nav.syfo.sykmelder.service

import io.ktor.util.InternalAPI
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import org.amshove.kluent.shouldEqual
import org.junit.Test

class SykmelderServiceTest {

    val pdlService = mockk<PdlPersonService>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val sykmelderService = SykmelderService(norskHelsenettClient, pdlService)

    @Test
    internal fun `Hent sykmelder`() {
        val hprNummer = "1234567"
        val fnr = "12345678910"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        coEvery { pdlService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn(fornavn, mellomnavn, etternavn),
                listOf(IdentInformasjon("ident", false, "FOLKEREGISTERIDENT"), IdentInformasjon("ident", false, "AKTORID"))
        )
        coEvery { norskHelsenettClient.finnBehandler(hprNummer, "callid") } returns Behandler(
                listOf(Godkjenning(
                        Kode(true, 1, null),
                        Kode(true, 1, null))),
                fnr,
                fornavn,
                mellomnavn,
                etternavn
        )

        runBlocking {
            val sykmelder = sykmelderService.hentSykmelder(hprNummer, "usertoken", "callid")

            sykmelder.hprNummer shouldEqual hprNummer
            sykmelder.fnr shouldEqual fnr
            sykmelder.fornavn shouldEqual fornavn
            sykmelder.mellomnavn shouldEqual mellomnavn
            sykmelder.etternavn shouldEqual etternavn
        }
    }

    @InternalAPI
    @Test
    internal fun `Feiler når hprNummer er tomt`() {
        val hprNummer = ""
        val fnr = "12345678910"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        coEvery { pdlService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn(fornavn, mellomnavn, etternavn),
                listOf(IdentInformasjon("ident", false, "FOLKEREGISTERIDENT"), IdentInformasjon("ident", false, "AKTORID"))
        )
        coEvery { norskHelsenettClient.finnBehandler(hprNummer, "callid") } returns Behandler(
                listOf(Godkjenning(
                        Kode(true, 1, null),
                        Kode(true, 1, null))),
                fnr,
                fornavn,
                mellomnavn,
                etternavn
        )

        runBlocking {
            val exception = assertFailsWith<IllegalStateException> {
                sykmelderService.hentSykmelder(hprNummer, "usertoken", "callid")
            }
            exception.message shouldEqual "HPR-nummer mangler"
        }
    }

    @InternalAPI
    @Test
    internal fun `Feiler når hpr returnerer null`() {
        val hprNummer = "1234567"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        coEvery { pdlService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn(fornavn, mellomnavn, etternavn),
                listOf(
                        IdentInformasjon("ident", false, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("ident", false, "AKTORID")
                )
        )
        coEvery { norskHelsenettClient.finnBehandler(hprNummer, "callid") } returns null

        runBlocking {
            val exception = assertFailsWith<IllegalStateException> {
                sykmelderService.hentSykmelder(hprNummer, "usertoken", "callid")
            }
            exception.message shouldEqual "Kunne ikke hente fnr for hpr $hprNummer"
        }
    }

    @InternalAPI
    @Test
    internal fun `Feiler når aktørid er null`() {
        val hprNummer = "1234567"
        val fnr = "12345678910"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        coEvery { pdlService.getPdlPerson(any(), any(), any()) } returns PdlPerson(
                Navn(fornavn, mellomnavn, etternavn),
                listOf(IdentInformasjon("ident", false, "FOLKEREGISTERIDENT"))
        )
        coEvery { norskHelsenettClient.finnBehandler(hprNummer, "callid") } returns Behandler(
                listOf(Godkjenning(
                        Kode(true, 1, null),
                        Kode(true, 1, null))),
                fnr,
                fornavn,
                mellomnavn,
                etternavn
        )

        runBlocking {
            val exception = assertFailsWith<IllegalStateException> {
                sykmelderService.hentSykmelder(hprNummer, "usertoken", "callid")
            }
            exception.message shouldEqual "Kunne ikke hente aktorId for hpr $hprNummer"
        }
    }
}
