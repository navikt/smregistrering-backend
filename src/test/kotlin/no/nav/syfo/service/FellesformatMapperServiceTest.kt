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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
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
            datoOpprettet = datoOpprettet,
        )

        assertEquals(10, receivedSykmelding.sykmelding.perioder.first().behandlingsdager)

        assertEquals(fnrPasient, receivedSykmelding.personNrPasient)
        assertEquals(fnrLege, receivedSykmelding.personNrLege)
        assertEquals(sykmeldingId, receivedSykmelding.navLogId)
        assertEquals(sykmeldingId, receivedSykmelding.msgId)
        assertEquals("", receivedSykmelding.legekontorOrgName)
        assertEquals(datoOpprettet, receivedSykmelding.mottattDato)
        assertEquals(null, receivedSykmelding.tssid)
        assertEquals(aktorId, receivedSykmelding.sykmelding.pasientAktoerId)
        assertEquals(true, receivedSykmelding.sykmelding.medisinskVurdering != null)
        assertEquals(
            Diagnose(
                system = "2.16.578.1.12.4.1.1.7170",
                kode = "A070",
                tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
            ),
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose,
        )
        assertEquals(
            Diagnose(
                system = "2.16.578.1.12.4.1.1.7170",
                kode = "U070",
                tekst = "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»",
            ),
            receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser.first(),
        )
        assertEquals(false, receivedSykmelding.sykmelding.skjermesForPasient)
        assertEquals(true, receivedSykmelding.sykmelding.arbeidsgiver != null)
        assertEquals(1, receivedSykmelding.sykmelding.perioder.size)
        assertEquals(null, receivedSykmelding.sykmelding.prognose)
        assertEquals(true, receivedSykmelding.sykmelding.utdypendeOpplysninger.toString().contains("Papirsykmeldingen inneholder utdypende opplysninger."))
        assertEquals(null, receivedSykmelding.sykmelding.tiltakArbeidsplassen)
        assertEquals(null, receivedSykmelding.sykmelding.tiltakNAV)
        assertEquals(null, receivedSykmelding.sykmelding.andreTiltak)
        assertEquals(false, receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart)
        assertEquals(null, receivedSykmelding.sykmelding.meldingTilArbeidsgiver)
        assertEquals(
            KontaktMedPasient(
                LocalDate.of(2020, 6, 23),
                "Ja nei det.",
            ),
            receivedSykmelding.sykmelding.kontaktMedPasient,
        )
        assertEquals(
            LocalDateTime.of(
                LocalDate.of(2020, 4, 1),
                LocalTime.NOON,
            ),
            receivedSykmelding.sykmelding.behandletTidspunkt,
        )
        assertNotNull(receivedSykmelding.sykmelding.behandler)
        assertEquals(AvsenderSystem("Papirsykmelding", journalpostId), receivedSykmelding.sykmelding.avsenderSystem)
        assertEquals(LocalDate.of(2020, 4, 1), receivedSykmelding.sykmelding.syketilfelleStartDato)
        assertEquals(datoOpprettet, receivedSykmelding.sykmelding.signaturDato)
        assertEquals(null, receivedSykmelding.sykmelding.navnFastlege)
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
                        arbeidsrelatertArsak = null,
                    ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false,
                ),
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "A070",
                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
                ),
                biDiagnoser = listOf(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
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
            harUtdypendeOpplysninger = false,
        )

        val fellesformat = getXmleiFellesformat(smRegisteringManuellt, sykmeldingId, datoOpprettet)

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = UUID.randomUUID().toString(),
            pasientAktoerId = aktorId,
            legeAktoerId = aktorIdLege,
            msgId = sykmeldingId,
            signaturDato = msgHead.msgInfo.genDate,
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
            vedlegg = null,
            utenlandskSykmelding = null,
        )

        assertEquals(fnrPasient, receivedSykmelding.personNrPasient)
        assertEquals(fnrLege, receivedSykmelding.personNrLege)
        assertEquals(sykmeldingId, receivedSykmelding.navLogId)
        assertEquals(sykmeldingId, receivedSykmelding.msgId)
        assertEquals("", receivedSykmelding.legekontorOrgName)
        assertEquals(datoOpprettet, receivedSykmelding.mottattDato)
        assertEquals(null, receivedSykmelding.tssid)
        assertEquals(aktorId, receivedSykmelding.sykmelding.pasientAktoerId)
        assertEquals(
            Diagnose(
                system = "2.16.578.1.12.4.1.1.7170",
                kode = "A070",
                tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
            ),
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose,
        )
        assertEquals(receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser, emptyList<Diagnose>())
        assertEquals(false, receivedSykmelding.sykmelding.medisinskVurdering.svangerskap)
        assertEquals(false, receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade)
        assertEquals(null, receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato)
        assertEquals(null, receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak)
        assertEquals(false, receivedSykmelding.sykmelding.skjermesForPasient)
        assertEquals(
            Arbeidsgiver(
                HarArbeidsgiver.EN_ARBEIDSGIVER,
                "NAV ikt",
                "Utvikler",
                100,
            ),
            receivedSykmelding.sykmelding.arbeidsgiver,
        )
        assertEquals(1, receivedSykmelding.sykmelding.perioder.size)
        assertEquals(AktivitetIkkeMulig(null, null), receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig)
        assertEquals(LocalDate.of(2019, Month.AUGUST, 15), receivedSykmelding.sykmelding.perioder[0].fom)
        assertEquals(LocalDate.of(2019, Month.SEPTEMBER, 30), receivedSykmelding.sykmelding.perioder[0].tom)
        assertEquals(null, receivedSykmelding.sykmelding.prognose)
        assertEquals(true, receivedSykmelding.sykmelding.utdypendeOpplysninger.isEmpty())
        assertEquals(null, receivedSykmelding.sykmelding.tiltakArbeidsplassen)
        assertEquals(null, receivedSykmelding.sykmelding.tiltakNAV)
        assertEquals(null, receivedSykmelding.sykmelding.andreTiltak)
        assertEquals(
            MeldingTilNAV(
                bistandUmiddelbart = false,
                beskrivBistand = "",
            ),
            receivedSykmelding.sykmelding.meldingTilNAV,
        )
        assertEquals(null, receivedSykmelding.sykmelding.meldingTilArbeidsgiver)
        assertEquals(
            KontaktMedPasient(
                LocalDate.of(2020, 6, 23),
                "Ja nei det.",
            ),
            receivedSykmelding.sykmelding.kontaktMedPasient,
        )
        assertEquals(
            LocalDateTime.of(
                LocalDate.of(2020, 4, 1),
                LocalTime.NOON,
            ),
            receivedSykmelding.sykmelding.behandletTidspunkt,
        )
        assertEquals(
            Behandler(
                fornavn = "Test",
                mellomnavn = "Bob",
                etternavn = "Doctor",
                aktoerId = aktorIdLege,
                fnr = fnrLege,
                hpr = "hpr",
                her = null,
                adresse = Adresse(null, null, null, null, null),
                tlf = "tel:55553336",
            ),
            receivedSykmelding.sykmelding.behandler,
        )
        assertEquals(AvsenderSystem("Papirsykmelding", journalpostId), receivedSykmelding.sykmelding.avsenderSystem)
        assertEquals(LocalDate.of(2020, 4, 1), receivedSykmelding.sykmelding.syketilfelleStartDato)
        assertEquals(datoOpprettet, receivedSykmelding.sykmelding.signaturDato)
        assertEquals(null, receivedSykmelding.sykmelding.navnFastlege)
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
                        arbeidsrelatertArsak = null,
                    ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false,
                ),
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "A070",
                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
                ),
                biDiagnoser = listOf(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
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
            harUtdypendeOpplysninger = false,
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        assertEquals(smRegisteringManuell.syketilfelleStartDato, tilSyketilfelleStartDato)
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
                        arbeidsrelatertArsak = null,
                    ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false,
                ),
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "A070",
                    tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
                ),
                biDiagnoser = listOf(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
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
            harUtdypendeOpplysninger = false,
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        assertEquals(smRegisteringManuell.perioder.first().fom, tilSyketilfelleStartDato)
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
            reisetilskudd = false,
        )

        val periode = tilHelseOpplysningerArbeidsuforhetPeriode(gradertPeriode)

        assertEquals(LocalDate.now().minusWeeks(1), periode.periodeFOMDato)
        assertEquals(LocalDate.now(), periode.periodeTOMDato)
        assertEquals(50, periode.gradertSykmelding.sykmeldingsgrad)
        assertEquals(true, periode.gradertSykmelding.isReisetilskudd)
        assertEquals(null, periode.aktivitetIkkeMulig)
        assertEquals(null, periode.behandlingsdager)
        assertEquals(false, periode.isReisetilskudd)
        assertEquals(null, periode.avventendeSykmelding)
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
                        arsak = listOf(MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET),
                    ),
                    arbeidsrelatertArsak = null,
                ),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = 10,
                gradert = null,
                reisetilskudd = false,
            ),
        ),
        medisinskVurdering = MedisinskVurdering(
            hovedDiagnose = Diagnose(
                system = "2.16.578.1.12.4.1.1.7170",
                kode = "A070",
                tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
            ),
            biDiagnoser = listOf(
                Diagnose(
                    system = "2.16.578.1.12.4.1.1.7170",
                    kode = "U070",
                    tekst = "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»",
                ),
            ),
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null,
            annenFraversArsak = null,
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
        harUtdypendeOpplysninger = harUtdypendeOpplysninger,
    )
}

const val journalpostId = "123"
