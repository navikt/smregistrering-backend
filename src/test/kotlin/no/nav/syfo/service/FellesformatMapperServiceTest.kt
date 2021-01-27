package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.UUID
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import no.nav.syfo.util.getReceivedSykmelding
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.junit.Test

class FellesformatMapperServiceTest {
    val sykmeldingId = "1234"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val datoOpprettet = LocalDateTime.now()

    @Test
    fun `Realistisk case ende-til-ende`() {
        val smRegisteringManuellt = getSmRegistreringManuell(fnrPasient, fnrLege)

        val receivedSykmelding = getReceivedSykmelding(
                manuell = smRegisteringManuellt,
                fnrPasient = fnrPasient,
                sykmelderFnr = smRegisteringManuellt.sykmelderFnr,
                datoOpprettet = datoOpprettet
        )

        receivedSykmelding.sykmelding.perioder.first().behandlingsdager shouldEqual 10

        receivedSykmelding.personNrPasient shouldEqual fnrPasient
        receivedSykmelding.personNrLege shouldEqual fnrLege
        receivedSykmelding.navLogId shouldEqual sykmeldingId
        receivedSykmelding.msgId shouldEqual sykmeldingId
        receivedSykmelding.legekontorOrgName shouldEqual ""
        receivedSykmelding.mottattDato shouldEqual datoOpprettet
        receivedSykmelding.tssid shouldEqual null
        receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
        receivedSykmelding.sykmelding.medisinskVurdering shouldNotEqual null
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldEqual Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )
        receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser.first() shouldEqual Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "U070",
            tekst = "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»"
        )
        receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
        receivedSykmelding.sykmelding.arbeidsgiver shouldNotEqual null
        receivedSykmelding.sykmelding.perioder.size shouldEqual 1
        receivedSykmelding.sykmelding.prognose shouldEqual Prognose(arbeidsforEtterPeriode = false, hensynArbeidsplassen = null, erIArbeid = ErIArbeid(egetArbeidPaSikt = false, annetArbeidPaSikt = false, arbeidFOM = null, vurderingsdato = null), erIkkeIArbeid = null)
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual "Pasienten trenger mer å gjøre"
        receivedSykmelding.sykmelding.tiltakNAV shouldEqual "Nei"
        receivedSykmelding.sykmelding.andreTiltak shouldEqual "Nei"
        receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldEqual false
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det.")
        receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual LocalDateTime.of(
            LocalDate.of(2020, 4, 1),
            LocalTime.NOON
        )
        receivedSykmelding.sykmelding.behandler shouldNotEqual null
        receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual LocalDate.of(2020, 4, 1)
        receivedSykmelding.sykmelding.signaturDato shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldEqual null
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
            utdypendeOpplysninger = null,
            prognose = Prognose(
                true,
                "Nei",
                ErIArbeid(
                    true,
                    false,
                    arbeidFOM = LocalDate.of(2020, 6, 23),
                    vurderingsdato = LocalDate.of(2020, 6, 23)
                ),
                null
            ),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            andreTiltak = "Nei",
            tiltakNAV = "Nei",
            tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), "")
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
        receivedSykmelding.sykmelding.arbeidsgiver shouldEqual Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, null, null, null)
        receivedSykmelding.sykmelding.perioder.size shouldEqual 1
        receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldEqual AktivitetIkkeMulig(null, null)
        receivedSykmelding.sykmelding.perioder[0].fom shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
        receivedSykmelding.sykmelding.perioder[0].tom shouldEqual LocalDate.of(2019, Month.SEPTEMBER, 30)
        receivedSykmelding.sykmelding.prognose shouldEqual Prognose(arbeidsforEtterPeriode = true, hensynArbeidsplassen = "Nei", erIArbeid = ErIArbeid(egetArbeidPaSikt = true, annetArbeidPaSikt = false, arbeidFOM = LocalDate.of(2020, 6, 23), vurderingsdato = LocalDate.of(2020, 6, 23)), erIkkeIArbeid = null)
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual "Pasienten trenger mer å gjøre"
        receivedSykmelding.sykmelding.tiltakNAV shouldEqual "Nei"
        receivedSykmelding.sykmelding.andreTiltak shouldEqual "Nei"
        receivedSykmelding.sykmelding.meldingTilNAV shouldEqual MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = "")
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det.")
        receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual LocalDateTime.of(
            LocalDate.of(2020, 4, 1),
            LocalTime.NOON
        )
        receivedSykmelding.sykmelding.behandler shouldEqual Behandler(
            fornavn = "Billy",
            mellomnavn = "Bob",
            etternavn = "Thornton",
            aktoerId = aktorIdLege,
            fnr = fnrLege,
            hpr = "hpr",
            her = null,
            adresse = Adresse(null, null, null, null, null),
            tlf = "tel:55553336"
        )
        receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual LocalDate.of(2020, 4, 1)
        receivedSykmelding.sykmelding.signaturDato shouldEqual datoOpprettet
        receivedSykmelding.sykmelding.navnFastlege shouldEqual null
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
            utdypendeOpplysninger = null,
            prognose = Prognose(
                true,
                "Nei",
                ErIArbeid(
                    true,
                    false,
                    arbeidFOM = LocalDate.of(2020, 6, 23),
                    vurderingsdato = LocalDate.of(2020, 6, 23)
                ),
                null
            ),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            andreTiltak = "Nei",
            tiltakNAV = "Nei",
            tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), "")
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        tilSyketilfelleStartDato shouldEqual smRegisteringManuell.syketilfelleStartDato
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
            utdypendeOpplysninger = null,
            prognose = Prognose(
                true,
                "Nei",
                ErIArbeid(
                    true,
                    false,
                    arbeidFOM = LocalDate.of(2020, 6, 23),
                    vurderingsdato = LocalDate.of(2020, 6, 23)
                ),
                null
            ),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            andreTiltak = "Nei",
            tiltakNAV = "Nei",
            tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), "")
        )

        val tilSyketilfelleStartDato = tilSyketilfelleStartDato(smRegisteringManuell)
        tilSyketilfelleStartDato shouldEqual smRegisteringManuell.perioder.first().fom
    }

    @Test
    fun `Utdypende opplysninger skal håndtere tomme maps`() {

        val stringMap = "{\n" +
                "  \"6.1\": {},\n" +
                "  \"6.2\": {},\n" +
                "  \"6.3\": {},\n" +
                "  \"6.4\": {},\n" +
                "  \"6.5\": {},\n" +
                "  \"6.6\": {}\n" +
                "}"
        val map = objectMapper.readValue<Map<String, Map<String, String>>>(stringMap)

        val tilUtdypendeOpplysninger = tilUtdypendeOpplysninger(map)
        tilUtdypendeOpplysninger.spmGruppe.size shouldEqual 0
    }

    @Test
    fun `Utdypende opplysninger skal håndtere maps med innhold`() {

        val stringMap = "{\n " +
                "  \"6.1\": {\"6.1.1\":\"bar\"},\n" +
                "  \"6.2\": {},\n" +
                "  \"6.3\": {},\n" +
                "  \"6.4\": {},\n" +
                "  \"6.5\": {},\n" +
                "  \"6.6\": {}\n" +
                "}"
        val map = objectMapper.readValue<Map<String, Map<String, String>>>(stringMap)

        val tilUtdypendeOpplysninger = tilUtdypendeOpplysninger(map)
        tilUtdypendeOpplysninger.spmGruppe.size shouldEqual 1
        val spmGruppe = tilUtdypendeOpplysninger.spmGruppe.first()
        spmGruppe.spmGruppeId shouldEqual "6.1"
        spmGruppe.spmGruppeTekst shouldEqual "Utdypende opplysninger ved 7/8,17 og 39 uker"
        val dynaSvarType = spmGruppe.spmSvar.first()
        dynaSvarType.spmId shouldEqual "6.1.1"
        dynaSvarType.spmTekst shouldEqual "Er det sykdommen, utredningen og/eller behandlingen som hindrer økt aktivitet? Beskriv."
        dynaSvarType.svarTekst shouldEqual "bar"
        dynaSvarType.restriksjon.restriksjonskode.size shouldEqual 1
        dynaSvarType.restriksjon.restriksjonskode.first().dn shouldEqual "Informasjonen skal ikke vises arbeidsgiver"
        dynaSvarType.restriksjon.restriksjonskode.first().v shouldEqual "A"
    }
}

fun getSmRegistreringManuell(fnrPasient: String, fnrLege: String): SmRegistreringManuell {
    return SmRegistreringManuell(
            pasientFnr = fnrPasient,
            sykmelderFnr = fnrLege,
            perioder = listOf(
                    Periode(
                            fom = LocalDate.now(),
                            tom = LocalDate.now(),

                            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(
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
            prognose = Prognose(arbeidsforEtterPeriode = false, hensynArbeidsplassen = null, erIArbeid = ErIArbeid(egetArbeidPaSikt = false, annetArbeidPaSikt = false, arbeidFOM = null, vurderingsdato = null), erIkkeIArbeid = null),
            utdypendeOpplysninger = null,
            syketilfelleStartDato = LocalDate.of(2020, 4, 1),
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, null, null, null),
            behandletDato = LocalDate.of(2020, 4, 1),
            kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            andreTiltak = "Nei",
            tiltakNAV = "Nei",
            tiltakArbeidsplassen = "Pasienten trenger mer å gjøre",
            navnFastlege = "Per Person",
            behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), "")
    )
}

fun getXmleiFellesformat(smRegisteringManuellt: SmRegistreringManuell, sykmeldingId: String, datoOpprettet: LocalDateTime): XMLEIFellesformat {
    return mapsmRegistreringManuelltTilFellesformat(
            smRegistreringManuell = smRegisteringManuellt,
            pdlPasient = PdlPerson(Navn("Billy", "Bob", "Thornton"), listOf(
                    IdentInformasjon(smRegisteringManuellt.pasientFnr, false, "FOLKEREGISTERIDENT")
            )),
            sykmelder = Sykmelder(aktorId = "aktorid", etternavn = "Thornton", fornavn = "Billy", mellomnavn = "Bob",
                    fnr = smRegisteringManuellt.sykmelderFnr, hprNummer = "hpr", godkjenninger = null),
            sykmeldingId = sykmeldingId,
            datoOpprettet = datoOpprettet
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
