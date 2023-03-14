package no.nav.syfo.pdl.service

import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.client.model.Identliste
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlResponse
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class PdlServiceTest {

    private val pdlClient = mockkClass(PdlClient::class)
    private val accessTokenClientV2 = mockkClass(AzureAdV2Client::class)
    private val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "scope")

    @Test
    internal fun `Hent person fra pdl uten fortrolig adresse`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

        runBlocking {
            val person = pdlService.getPdlPerson("01245678901", "callId")
            assertEquals("fornavn", person.navn.fornavn)
            assertEquals(null, person.navn.mellomnavn)
            assertEquals("etternavn", person.navn.etternavn)
            assertEquals("987654321", person.aktorId)
        }
    }

    @Test
    internal fun `Skal feile naar person ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(null, null),
            errors = null
        )

        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        assertEquals("Klarte ikke hente ut person fra PDL", exception.message)
    }

    @Test
    internal fun `Skal feile naar navn er tom liste`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentPerson = HentPerson(
                    navn = emptyList()
                ),
                hentIdenter = Identliste(emptyList())
            ),
            errors = null
        )
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        assertEquals("Fant ikke navn på person i PDL", exception.message)
    }

    @Test
    internal fun `Skal feile naar navn ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentPerson = HentPerson(
                    navn = null
                ),
                hentIdenter = Identliste(
                    listOf(
                        IdentInformasjon(
                            ident = "987654321",
                            gruppe = "foo",
                            historisk = false
                        )
                    )
                )
            ),
            errors = null
        )
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        assertEquals("Fant ikke navn på person i PDL", exception.message)
    }

    @Test
    internal fun `Skal feile naar aktorid ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns "token"
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentPerson = HentPerson(
                    navn = listOf(Navn("fornavn", "mellomnavn", "etternavn"))
                ),
                hentIdenter = Identliste(emptyList())
            ),
            errors = null
        )
        val exception = assertFailsWith<AktoerNotFoundException> {
            runBlocking {
                pdlService.getPdlPerson("123", "callId")
            }
        }
        assertEquals("Fant ikke aktørId i PDL", exception.message)
    }
}
