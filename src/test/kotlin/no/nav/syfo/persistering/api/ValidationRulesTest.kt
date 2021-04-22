package no.nav.syfo.persistering.api

import java.time.LocalDate
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.Periode
import no.nav.syfo.model.RuleHitCustomError
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.getSmRegistreringManuell
import org.amshove.kluent.shouldEqual
import org.junit.Test

class ValidationRulesTest {

    @Test
    fun `Overlappende perioder gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            ),
            Periode(
                fom = LocalDate.of(2020, 9, 8),
                tom = LocalDate.of(2020, 9, 20),
                aktivitetIkkeMulig = null,
                gradert = Gradert(reisetilskudd = false, grad = 50),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            )
        )

        harOverlappendePerioder(perioder) shouldEqual true
    }

    @Test
    fun `Identiske perioder gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            ),
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            )
        )

        harOverlappendePerioder(perioder) shouldEqual true
    }

    @Test
    fun `Perioder uten overlapp gir ikke valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            ),
            Periode(
                fom = LocalDate.of(2020, 9, 11),
                tom = LocalDate.of(2020, 9, 20),
                aktivitetIkkeMulig = null,
                gradert = Gradert(reisetilskudd = false, grad = 50),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            )
        )

        harOverlappendePerioder(perioder) shouldEqual false
    }

    @Test
    fun `Kun reisetilskudd gir ikke valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = null,
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual false
    }

    @Test
    fun `Reisetilskudd og aktivitetIkkeMulig gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual true
    }

    @Test
    fun `Reisetilskudd og gradert gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = null,
                gradert = Gradert(reisetilskudd = false, grad = 50),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual true
    }

    @Test
    fun `Reisetilskudd og avventende gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = null,
                gradert = null,
                avventendeInnspillTilArbeidsgiver = "Innspill",
                behandlingsdager = null,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual true
    }

    @Test
    fun `Reisetilskudd og behandlingsdager gir valideringsfeil`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = null,
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = 5,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual true
    }

    @Test
    fun `Reisetilskudd og aktivitetIkkeMulig gir ikke valideringsfeil hvis perioder ikke overlapper`() {
        val perioder = listOf(
            Periode(
                fom = LocalDate.of(2020, 9, 1),
                tom = LocalDate.of(2020, 9, 10),
                aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = false
            ),
            Periode(
                fom = LocalDate.of(2020, 9, 11),
                tom = LocalDate.of(2020, 9, 20),
                aktivitetIkkeMulig = null,
                gradert = null,
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                reisetilskudd = true
            )
        )

        harUlovligKombinasjonMedReisetilskudd(perioder) shouldEqual false
    }

    @Test
    fun `behandletDato frem i tid gir valideringsfeil`() {
        val behandletDato = LocalDate.now().plusDays(1)

        erFremtidigDato(behandletDato) shouldEqual true
    }

    @Test
    fun `behandletDato idag gir ikke valideringsfeil`() {
        val behandletDato = LocalDate.now()

        erFremtidigDato(behandletDato) shouldEqual false
    }

    @Test
    fun `studentLisensSkalKasteFeil`() {
        val smRegistreringManuell = getSmRegistreringManuell("12345678912", "12345678912",
            false)
        val sykmelder = Sykmelder("hpr", "12345678912", null, null, null,
            null,
            listOf(Godkjenning(helsepersonellkategori = null, autorisasjon = Kode(true, 7704, "17"))))
        val validationResult = ValidationResult(Status.MANUAL_PROCESSING,
            ruleHits = listOf(RuleInfo(ruleName = RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name,
                messageForUser = "", messageForSender = "", ruleStatus = Status.MANUAL_PROCESSING)))
        checkValidState(smRegistreringManuell, sykmelder, validationResult = validationResult)
    }
}
