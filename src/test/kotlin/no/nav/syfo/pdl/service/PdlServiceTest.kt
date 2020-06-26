package no.nav.syfo.pdl.service

import io.mockk.coEvery
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.client.model.Identliste
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)
    coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    describe("PdlService") {
        it("hente person fra pdl uten fortrolig dresse") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns getPdlResponse()
            runBlocking {
                val person = pdlService.getPdlPerson("01245678901", "Bearer token", "callId")
                person.navn.fornavn shouldEqual "fornavn"
                person.navn.mellomnavn shouldEqual null
                person.navn.etternavn shouldEqual "etternavn"
                person.aktorId shouldEqual "987654321"
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(ResponseData(null, null))
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPdlPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke person i PDL"
        }

        it("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                navn = emptyList()
            ),
                hentIdenter = Identliste(emptyList())
            ))
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPdlPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke navn på person i PDL"
        }
        it("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                navn = null
            ),
                hentIdenter = Identliste(listOf(IdentInformasjon(ident = "987654321")))
            ))
            val exception = assertFailsWith<PersonNotFoundInPdl> {
                runBlocking {
                    pdlService.getPdlPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke navn på person i PDL"
        }


        it("Skal feile når aktørid ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                navn = listOf(Navn("fornavn", "mellomnavn", "etternavn"))
            ),
                hentIdenter = Identliste(emptyList())
            ))
            val exception = assertFailsWith<AktoerNotFoundException> {
                runBlocking {
                    pdlService.getPdlPerson("123", "Bearer token", "callId")
                }
            }
            exception.message shouldEqual "Fant ikke aktørId i PDL"
        }
    }
})
