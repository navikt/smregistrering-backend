package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.client.model.Identliste
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlResponse
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.shouldEqual
import org.junit.Test

@KtorExperimentalAPI
internal class PdlServiceTest {

    private val pdlClient = mockkClass(PdlClient::class)
    private val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    private val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "scope")

    @Test
    internal fun `Hent person fra pdl uten fortrolig adresse`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

        runBlocking {
            val person = pdlService.getPdlPerson("01245678901", "callId")
            person.navn.fornavn shouldEqual "fornavn"
            person.navn.mellomnavn shouldEqual null
            person.navn.etternavn shouldEqual "etternavn"
            person.aktorId shouldEqual "987654321"
        }
    }

    @Test
    internal fun `Skal feile når person ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(PdlResponse(null, null), errors = null)

        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        exception.message shouldEqual "Klarte ikke hente ut person fra PDL"
    }

    @Test
    internal fun `Skal feile når navn er tom liste`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(PdlResponse(hentPerson = HentPerson(
            navn = emptyList()
        ),
            hentIdenter = Identliste(emptyList())
        ), errors = null)
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        exception.message shouldEqual "Fant ikke navn på person i PDL"
    }

    @Test
    internal fun `Skal feile når navn ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(PdlResponse(hentPerson = HentPerson(
            navn = null
        ),
            hentIdenter = Identliste(listOf(IdentInformasjon(ident = "987654321", gruppe = "foo", historisk = false)))
        ), errors = null)
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        exception.message shouldEqual "Fant ikke navn på person i PDL"
    }

    @Test
    internal fun `Skal feile når aktørid ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(PdlResponse(hentPerson = HentPerson(
            navn = listOf(Navn("fornavn", "mellomnavn", "etternavn"))
        ),
            hentIdenter = Identliste(emptyList())
        ), errors = null)
        val exception = assertFailsWith<AktoerNotFoundException> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        exception.message shouldEqual "Fant ikke aktørId i PDL"
    }
}
