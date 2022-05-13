package no.nav.syfo.sykmelder.service

import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.log
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException

class SykmelderService(
    private val norskHelsenettClient: NorskHelsenettClient,
    private val pdlPersonService: PdlPersonService
) {

    suspend fun hentSykmelder(hprNummer: String, callId: String): Sykmelder {
        if (hprNummer.isEmpty()) {
            log.warn("HPR-nummer mangler, kan ikke fortsette")
            throw IllegalStateException("HPR-nummer mangler")
        }

        val behandler = norskHelsenettClient.finnBehandler(hprNummer, callId)

        if (behandler.fnr.isNullOrEmpty()) {
            log.warn("Kunne ikke hente fnr for hpr {}", hprNummer)
            throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")
        }

        val pdlPerson = pdlPersonService.getPdlPerson(behandler.fnr, callId)

        if (pdlPerson.aktorId == null) {
            log.warn("Fant ikke aktorId til behandler for HPR {}", hprNummer)
            throw SykmelderNotFoundException("Kunne ikke hente aktorId for hpr $hprNummer")
        }

        return Sykmelder(
            hprNummer = hprNummer,
            fnr = behandler.fnr,
            aktorId = pdlPerson.aktorId,
            fornavn = pdlPerson.navn.fornavn,
            mellomnavn = pdlPerson.navn.mellomnavn,
            etternavn = pdlPerson.navn.etternavn,
            godkjenninger = behandler.godkjenninger
        )
    }
}
