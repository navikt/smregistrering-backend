package no.nav.syfo.service

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.objectMapper
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import no.nav.syfo.util.getReceivedSykmelding
import no.nav.syfo.util.getXmleiFellesformat
import no.nav.syfo.util.tilHelseOpplysningerArbeidsuforhetPeriode
import no.nav.syfo.util.tilSyketilfelleStartDato
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.UUID

class FellesformatMapperServiceTest {
    val sykmeldingId = "1234"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val datoOpprettet = LocalDateTime.now()

    @Test
    fun `Realistisk case ende-til-ende`() {
        val smRegisteringManuellt = getSmRegistreringManuell(fnrPasient, fnrLege, harUtdypendeOpplysninger = true)

        val receivedSykmelding = getReceivedSykmelding(
            manuell = smRegisteringManuellt,
            fnrPasient = fnrPasient,
            sykmelderFnr = smRegisteringManuellt.sykmelderFnr,
            datoOpprettet = datoOpprettet
        )

        receivedSykmelding.sykmelding.perioder.first().behandlingsdager shouldBeEqualTo 10

        receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
        receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
        receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId
        receivedSykmelding.msgId shouldBeEqualTo sykmeldingId
        receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
        receivedSykmelding.mottattDato shouldBeEqualTo datoOpprettet
        receivedSykmelding.tssid shouldBeEqualTo null
        receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo aktorId
        receivedSykmelding.sykmelding.medisinskVurdering shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldBeEqualTo Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )
        receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser.first() shouldBeEqualTo Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "U070",
            tekst = "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»"
        )
        receivedSykmelding.sykmelding.skjermesForPasient shouldBeEqualTo false
        receivedSykmelding.sykmelding.arbeidsgiver shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.perioder.size shouldBeEqualTo 1
        receivedSykmelding.sykmelding.prognose shouldBeEqualTo null
        receivedSykmelding.sykmelding.utdypendeOpplysninger.toString()
            .shouldContain("Papirsykmeldingen inneholder utdypende opplysninger.")
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldBeEqualTo null
        receivedSykmelding.sykmelding.tiltakNAV shouldBeEqualTo null
        receivedSykmelding.sykmelding.andreTiltak shouldBeEqualTo null
        receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldBeEqualTo false
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldBeEqualTo null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(
            LocalDate.of(2020, 6, 23),
            "Ja nei det."
        )
        receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo LocalDateTime.of(
            LocalDate.of(2020, 4, 1),
            LocalTime.NOON
        )
        receivedSykmelding.sykmelding.behandler shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("Papirsykmelding", journalpostId)
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2020, 4, 1)
        receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
    }

    @Test
    fun `Minimal input fra frontend`() {
        val smRegisteringManuellt = SmRegistreringManuell(
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
            ),
            syketilfelleStartDato = LocalDate.of(2020, 4, 1),
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
            behandletDato = LocalDate.of(2020, 4, 1),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), ""),
            harUtdypendeOpplysninger = false
        )

        val fellesformat = getXmleiFellesformat(smRegisteringManuellt, sykmeldingId, datoOpprettet)

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
            tssid = null,
            merknader = null,
            partnerreferanse = null,
            legeHelsepersonellkategori = "LE",
            legeHprNr = "hpr",
            vedlegg = null
        )

        receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
        receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
        receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId
        receivedSykmelding.msgId shouldBeEqualTo sykmeldingId
        receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
        receivedSykmelding.mottattDato shouldBeEqualTo datoOpprettet
        receivedSykmelding.tssid shouldBeEqualTo null
        receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo aktorId
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldBeEqualTo Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )
        receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser shouldBeEqualTo emptyList()
        receivedSykmelding.sykmelding.medisinskVurdering.svangerskap shouldBeEqualTo false
        receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade shouldBeEqualTo false
        receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato shouldBeEqualTo null
        receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak shouldBeEqualTo null
        receivedSykmelding.sykmelding.skjermesForPasient shouldBeEqualTo false
        receivedSykmelding.sykmelding.arbeidsgiver shouldBeEqualTo Arbeidsgiver(
            HarArbeidsgiver.EN_ARBEIDSGIVER,
            "NAV ikt",
            "Utvikler",
            100
        )
        receivedSykmelding.sykmelding.perioder.size shouldBeEqualTo 1
        receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldBeEqualTo AktivitetIkkeMulig(null, null)
        receivedSykmelding.sykmelding.perioder[0].fom shouldBeEqualTo LocalDate.of(2019, Month.AUGUST, 15)
        receivedSykmelding.sykmelding.perioder[0].tom shouldBeEqualTo LocalDate.of(2019, Month.SEPTEMBER, 30)
        receivedSykmelding.sykmelding.prognose shouldBeEqualTo null
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldBeEqualTo emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldBeEqualTo null
        receivedSykmelding.sykmelding.tiltakNAV shouldBeEqualTo null
        receivedSykmelding.sykmelding.andreTiltak shouldBeEqualTo null
        receivedSykmelding.sykmelding.meldingTilNAV shouldBeEqualTo MeldingTilNAV(
            bistandUmiddelbart = false,
            beskrivBistand = ""
        )
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldBeEqualTo null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(
            LocalDate.of(2020, 6, 23),
            "Ja nei det."
        )
        receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo LocalDateTime.of(
            LocalDate.of(2020, 4, 1),
            LocalTime.NOON
        )
        receivedSykmelding.sykmelding.behandler shouldBeEqualTo Behandler(
            fornavn = "Test",
            mellomnavn = "Bob",
            etternavn = "Doctor",
            aktoerId = aktorIdLege,
            fnr = fnrLege,
            hpr = "hpr",
            her = null,
            adresse = Adresse(null, null, null, null, null),
            tlf = "tel:55553336"
        )
        receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("Papirsykmelding", journalpostId)
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2020, 4, 1)
        receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
    }

    @Test
    fun `Test av tilSyketilfelleStartDato dato er satt`() {
        val smRegisteringManuell = SmRegistreringManuell(
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
            ),
            syketilfelleStartDato = LocalDate.of(2020, 4, 1),
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
            behandletDato = LocalDate.of(2020, 4, 1),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), ""),
            harUtdypendeOpplysninger = false
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        tilSyketilfelleStartDato shouldBeEqualTo smRegisteringManuell.syketilfelleStartDato
    }

    @Test
    fun `Test av tilSyketilfelleStartDato dato ikke satt`() {
        val smRegisteringManuell = SmRegistreringManuell(
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
            ),
            syketilfelleStartDato = null,
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
            behandletDato = LocalDate.of(2020, 4, 1),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), ""),
            harUtdypendeOpplysninger = false
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        tilSyketilfelleStartDato shouldBeEqualTo smRegisteringManuell.perioder.first().fom
    }

    @Test
    fun `Skal ikke sette aktivitetIkkeMulig hvis denne ikke er satt fra frontend`() {
        val gradertPeriode = Periode(
            fom = LocalDate.now().minusWeeks(1),
            tom = LocalDate.now(),
            aktivitetIkkeMulig = null,
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = Gradert(reisetilskudd = true, grad = 50),
            reisetilskudd = false
        )

        val periode = tilHelseOpplysningerArbeidsuforhetPeriode(gradertPeriode)

        periode.periodeFOMDato shouldBeEqualTo LocalDate.now().minusWeeks(1)
        periode.periodeTOMDato shouldBeEqualTo LocalDate.now()
        periode.gradertSykmelding.sykmeldingsgrad shouldBeEqualTo 50
        periode.gradertSykmelding.isReisetilskudd shouldBeEqualTo true
        periode.aktivitetIkkeMulig shouldBeEqualTo null
        periode.behandlingsdager shouldBeEqualTo null
        periode.isReisetilskudd shouldBeEqualTo false
        periode.avventendeSykmelding shouldBeEqualTo null
    }
}

fun getSmRegistreringManuell(fnrPasient: String, fnrLege: String, harUtdypendeOpplysninger: Boolean = false): SmRegistreringManuell {
    return SmRegistreringManuell(
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
                behandlingsdager = 10,
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
            biDiagnoser = listOf(
                Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "U070",
                    tekst = "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»"
                )
            ),
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null,
            annenFraversArsak = null
        ),
        syketilfelleStartDato = LocalDate.of(2020, 4, 1),
        skjermesForPasient = false,
        arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
        behandletDato = LocalDate.of(2020, 4, 1),
        kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
        meldingTilArbeidsgiver = null,
        meldingTilNAV = null,
        navnFastlege = "Per Person",
        behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), ""),
        harUtdypendeOpplysninger = harUtdypendeOpplysninger
    )
}

const val journalpostId = "123"
