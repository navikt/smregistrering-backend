package no.nav.syfo.sykmelder.service

import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.log
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.util.changeHelsepersonellkategoriVerdiFromFAToFA1

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

        // Helsedir har ikke migriert alle med Helsepersonellkategori(OID=9060) Verdien FA over til FA1 eller FA2,
        // da det var mulighet at noe måtte ligge igjen for historiske årsaker
        val godkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(behandler.godkjenninger)

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
            godkjenninger = godkjenninger
        )
    }
}
