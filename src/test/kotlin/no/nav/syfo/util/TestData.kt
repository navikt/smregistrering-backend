package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.getSmRegistreringManuell
import no.nav.syfo.service.journalpostId
import no.nav.syfo.service.toSykmelding
import java.time.LocalDateTime

fun getReceivedSykmelding(manuell: SmRegistreringManuell = getSmRegistreringManuell("fnrPasient", "fnrLege"), fnrPasient: String, sykmelderFnr: String, datoOpprettet: LocalDateTime = LocalDateTime.now(), sykmeldingId: String = "1234"): ReceivedSykmelding {
    val fellesformat = getXmleiFellesformat(manuell, sykmeldingId, datoOpprettet)
    val sykmelding = getSykmelding(extractHelseOpplysningerArbeidsuforhet(fellesformat), fellesformat.get(), sykmeldingId = sykmeldingId)
    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    return ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = fnrPasient,
        tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
        personNrLege = sykmelderFnr,
        navLogId = sykmelding.id,
        msgId = sykmelding.id,
        legekontorOrgNr = null,
        legekontorOrgName = "",
        legekontorHerId = null,
        legekontorReshId = null,
        mottattDato = datoOpprettet,
        rulesetVersion = healthInformation.regelSettVersjon,
        fellesformat = fellesformatMarshaller.toString(fellesformat),
        tssid = null,
        merknader = null,
        partnerreferanse = null,
        legeHelsepersonellkategori = "LE",
        legeHprNr = "hpr",
        vedlegg = null,
        utenlandskSykmelding = null
    )
}

fun getXmleiFellesformat(smRegisteringManuellt: SmRegistreringManuell, sykmeldingId: String, datoOpprettet: LocalDateTime): XMLEIFellesformat {
    return mapsmRegistreringManuelltTilFellesformat(
        smRegistreringManuell = smRegisteringManuellt,
        pdlPasient = PdlPerson(
            Navn("Test", "Doctor", "Thornton"),
            listOf(
                IdentInformasjon(smRegisteringManuellt.pasientFnr, false, "FOLKEREGISTERIDENT")
            )
        ),
        sykmelder = Sykmelder(
            aktorId = "aktorid", etternavn = "Doctor", fornavn = "Test", mellomnavn = "Bob",
            fnr = smRegisteringManuellt.sykmelderFnr, hprNummer = "hpr", godkjenninger = null
        ),
        sykmeldingId = sykmeldingId,
        datoOpprettet = datoOpprettet,
        journalpostId = journalpostId
    )
}

fun getSykmelding(healthInformation: HelseOpplysningerArbeidsuforhet, msgHead: XMLMsgHead, sykmeldingId: String = "1234", aktorId: String = "aktorId", aktorIdLege: String = "aktorIdLege"): Sykmelding {
    return healthInformation.toSykmelding(
        sykmeldingId = sykmeldingId,
        pasientAktoerId = aktorId,
        legeAktoerId = aktorIdLege,
        msgId = sykmeldingId,
        signaturDato = msgHead.msgInfo.genDate
    )
}
