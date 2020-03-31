package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegisteringManuellt
import no.nav.syfo.objectMapper
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.junit.Test

internal class FellesformatMapperServiceTest {
    val sykmeldingId = "1234"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val hprNummer = "10052512"
    val datoOpprettet = LocalDateTime.now()

    @Test
    internal fun `Realistisk case ende-til-ende`() {
        val smRegisteringManuellt = SmRegisteringManuellt(
            pasientFnr = fnrPasient,
            sykmelderFnr = fnrLege,
            perioder = listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(
                        medisinskArsak = MedisinskArsak(
                            beskrivelse = "test data",
                            arsak = listOf(MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET)
                        ),
                        arbeidsrelatertArsak = null
                    ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false
                )
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "A070",
                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
                ),
                biDiagnoser = listOf(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null
            )
        )

        val fellesformat = mapsmRegisteringManuelltTilFellesformat(
            smRegisteringManuellt = smRegisteringManuellt,
            pasientFnr = smRegisteringManuellt.pasientFnr,
            sykmelderFnr = smRegisteringManuellt.sykmelderFnr,
            sykmeldingId = sykmeldingId,
            datoOpprettet = datoOpprettet
        )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = UUID.randomUUID().toString(),
            pasientAktoerId = aktorId,
            legeAktoerId = aktorIdLege,
            msgId = sykmeldingId,
            signaturDato = msgHead.msgInfo.genDate
        )

        val receivedSykmelding = ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = fnrPasient,
            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = smRegisteringManuellt.sykmelderFnr,
            navLogId = sykmeldingId,
            msgId = sykmeldingId,
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = datoOpprettet,
            rulesetVersion = healthInformation.regelSettVersjon,
            fellesformat = objectMapper.writeValueAsString(fellesformat),
            tssid = null
        )

        receivedSykmelding.personNrPasient shouldEqual fnrPasient
        receivedSykmelding.personNrLege shouldEqual fnrLege
        receivedSykmelding.navLogId shouldEqual sykmeldingId
        receivedSykmelding.msgId shouldEqual sykmeldingId
        receivedSykmelding.legekontorOrgName shouldEqual ""
        receivedSykmelding.mottattDato shouldEqual datoOpprettet
        receivedSykmelding.tssid shouldEqual null
        receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
        receivedSykmelding.sykmelding.medisinskVurdering shouldNotEqual null
        receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
        receivedSykmelding.sykmelding.arbeidsgiver shouldNotEqual null
        receivedSykmelding.sykmelding.perioder.size shouldEqual 1
        receivedSykmelding.sykmelding.prognose shouldEqual null
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual null
        receivedSykmelding.sykmelding.tiltakNAV shouldEqual null
        receivedSykmelding.sykmelding.andreTiltak shouldEqual null
        receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldEqual null
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(null, null)
        receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.behandler shouldNotEqual null
        receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual smRegisteringManuellt.perioder.first().fom
        receivedSykmelding.sykmelding.signaturDato shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldEqual null
    }

    @Test
    internal fun `Minimal ocr-fil`() {
        val smRegisteringManuellt = SmRegisteringManuellt(
            pasientFnr = fnrPasient,
            sykmelderFnr = fnrLege,
            perioder = listOf(
                Periode(
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(
                        medisinskArsak = null,
                        arbeidsrelatertArsak = null
                    ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false
                )
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "A070",
                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
                ),
                biDiagnoser = listOf(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null
            )
        )

        val fellesformat = mapsmRegisteringManuelltTilFellesformat(
            smRegisteringManuellt = smRegisteringManuellt,
            pasientFnr = smRegisteringManuellt.pasientFnr,
            sykmelderFnr = smRegisteringManuellt.sykmelderFnr,
            sykmeldingId = sykmeldingId,
            datoOpprettet = datoOpprettet
        )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = UUID.randomUUID().toString(),
            pasientAktoerId = aktorId,
            legeAktoerId = aktorIdLege,
            msgId = sykmeldingId,
            signaturDato = msgHead.msgInfo.genDate
        )

        val receivedSykmelding = ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = fnrPasient,
            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = fnrLege,
            navLogId = sykmeldingId,
            msgId = sykmeldingId,
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = datoOpprettet,
            rulesetVersion = healthInformation.regelSettVersjon,
            fellesformat = objectMapper.writeValueAsString(fellesformat),
            tssid = null
        )

        receivedSykmelding.personNrPasient shouldEqual fnrPasient
        receivedSykmelding.personNrLege shouldEqual fnrLege
        receivedSykmelding.navLogId shouldEqual sykmeldingId
        receivedSykmelding.msgId shouldEqual sykmeldingId
        receivedSykmelding.legekontorOrgName shouldEqual ""
        receivedSykmelding.mottattDato shouldEqual datoOpprettet
        receivedSykmelding.tssid shouldEqual null
        receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldEqual Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )
        receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser shouldEqual emptyList()
        receivedSykmelding.sykmelding.medisinskVurdering.svangerskap shouldEqual false
        receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade shouldEqual false
        receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato shouldEqual null
        receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak shouldEqual null
        receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
        receivedSykmelding.sykmelding.arbeidsgiver shouldEqual Arbeidsgiver(
            HarArbeidsgiver.EN_ARBEIDSGIVER,
            null,
            null,
            null
        )
        receivedSykmelding.sykmelding.perioder.size shouldEqual 1
        receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldEqual AktivitetIkkeMulig(null, null)
        receivedSykmelding.sykmelding.perioder[0].fom shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
        receivedSykmelding.sykmelding.perioder[0].tom shouldEqual LocalDate.of(2019, Month.SEPTEMBER, 30)
        receivedSykmelding.sykmelding.prognose shouldEqual null
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual null
        receivedSykmelding.sykmelding.tiltakNAV shouldEqual null
        receivedSykmelding.sykmelding.andreTiltak shouldEqual null
        receivedSykmelding.sykmelding.meldingTilNAV shouldEqual null
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(null, null)
        receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.behandler shouldEqual Behandler(
            fornavn = "",
            mellomnavn = null,
            etternavn = "",
            aktoerId = aktorIdLege,
            fnr = fnrLege,
            hpr = null,
            her = null,
            adresse = Adresse(null, null, null, null, null),
            tlf = "tel:55553336"
        )
        receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
        receivedSykmelding.sykmelding.signaturDato shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldEqual null
    }
}
