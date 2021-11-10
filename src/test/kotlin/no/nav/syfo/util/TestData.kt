package no.nav.syfo.util

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.service.getSmRegistreringManuell
import no.nav.syfo.service.getSykmelding
import no.nav.syfo.service.getXmleiFellesformat
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
        legeHprNr = "hpr"
    )
}
