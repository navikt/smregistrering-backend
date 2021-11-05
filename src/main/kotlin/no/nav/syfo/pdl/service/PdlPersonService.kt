package no.nav.syfo.pdl.service

import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val azureAdV2Client: AzureAdV2Client,
    private val pdlScope: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }

    suspend fun getPdlPerson(fnr: String, callId: String): PdlPerson {
        val token = azureAdV2Client.getAccessToken(pdlScope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente accessToken for PDL")
        val pdlResponse = pdlClient.getPerson(fnr, token)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL kastet error: {} ", it)
            }
        }
        if (pdlResponse.data.hentPerson == null) {
            log.warn("Klarte ikke hente ut person fra PDL {}", callId)
            throw PersonNotFoundInPdl("Klarte ikke hente ut person fra PDL")
        }
        if (pdlResponse.data.hentPerson.navn.isNullOrEmpty()) {
            log.warn("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isNullOrEmpty()) {
            log.warn("Fant ikke aktørid i PDL {}", callId)
            throw AktoerNotFoundException("Fant ikke aktørId i PDL")
        }
        return PdlPerson(getNavn(pdlResponse.data.hentPerson.navn[0]), pdlResponse.data.hentIdenter.identer)
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
