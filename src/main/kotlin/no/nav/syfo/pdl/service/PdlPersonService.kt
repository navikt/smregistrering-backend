package no.nav.syfo.pdl.service

import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import org.slf4j.LoggerFactory

class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }
    suspend fun getPdlPerson(fnr: String, userToken: String, callId: String): PdlPerson {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(fnr, userToken, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL kastet error: {} ", it)
            }
        }
        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.hentPerson.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isNullOrEmpty()) {
            log.error("Fant ikke aktørid i PDL {}", callId)
            throw AktoerNotFoundException("Fant ikke aktørId i PDL")
        }
        return PdlPerson(getNavn(pdlResponse.data.hentPerson.navn[0]), pdlResponse.data.hentIdenter.identer.first().ident)
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
