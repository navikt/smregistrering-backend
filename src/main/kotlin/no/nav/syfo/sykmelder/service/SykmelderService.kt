package no.nav.syfo.sykmelder.service

import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.log
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.pdl.service.PdlPersonService

class SykmelderService(
    private val norskHelsenettClient: NorskHelsenettClient,
    private val pdlPersonService: PdlPersonService
) {

    suspend fun hentSykmelder(hprNummer: String, userToken: String, callId: String): Sykmelder {
        if (hprNummer.isEmpty()) {
            log.warn("HPR-nummer mangler, kan ikke fortsette")
            throw IllegalStateException("HPR-nummer mangler")
        }

        val behandlerFraHpr = norskHelsenettClient.finnBehandler(hprNummer, callId)

        if (behandlerFraHpr == null || behandlerFraHpr.fnr.isNullOrEmpty()) {
            log.warn("Kunne ikke hente fnr for hpr {}", hprNummer)
            throw IllegalStateException("Kunne ikke hente fnr for hpr $hprNummer")
        }

        val behandler = pdlPersonService.getPdlPerson(behandlerFraHpr.fnr, userToken, callId)

        if (behandler.aktorId == null) {
            log.warn("Fant ikke aktorId til behandler for HPR {}", hprNummer)
            throw IllegalStateException("Kunne ikke hente aktorId for hpr $hprNummer")
        }

        return Sykmelder(
            hprNummer = hprNummer,
            fnr = behandlerFraHpr.fnr,
            aktorId = behandler.aktorId,
            fornavn = behandler.navn.fornavn,
            mellomnavn = behandler.navn.mellomnavn,
            etternavn = behandler.navn.etternavn
        )
    }
}
