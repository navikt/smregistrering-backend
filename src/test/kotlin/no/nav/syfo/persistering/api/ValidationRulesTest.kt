package no.nav.syfo.persistering.api

import java.time.LocalDate
import kotlin.test.assertFailsWith
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValidationRulesTest {

    @Test
    fun `Overlappende perioder gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
                Periode(
                    fom = LocalDate.of(2020, 9, 8),
                    tom = LocalDate.of(2020, 9, 20),
                    aktivitetIkkeMulig = null,
                    gradert = Gradert(reisetilskudd = false, grad = 50),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
            )

        assertEquals(true, harOverlappendePerioder(perioder))
    }

    @Test
    fun `Identiske perioder gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
            )

        assertEquals(true, harOverlappendePerioder(perioder))
    }

    @Test
    fun `Perioder uten overlapp gir ikke valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
                Periode(
                    fom = LocalDate.of(2020, 9, 11),
                    tom = LocalDate.of(2020, 9, 20),
                    aktivitetIkkeMulig = null,
                    gradert = Gradert(reisetilskudd = false, grad = 50),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
            )

        assertEquals(false, harOverlappendePerioder(perioder))
    }

    @Test
    fun `Kun reisetilskudd gir ikke valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig = null,
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = true,
                ),
            )

        assertEquals(false, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `Reisetilskudd og aktivitetIkkeMulig gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = true,
                ),
            )

        assertEquals(true, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `Reisetilskudd og gradert gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig = null,
                    gradert = Gradert(reisetilskudd = false, grad = 50),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = true,
                ),
            )

        assertEquals(true, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `Reisetilskudd og avventende gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig = null,
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = "Innspill",
                    behandlingsdager = null,
                    reisetilskudd = true,
                ),
            )

        assertEquals(true, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `Reisetilskudd og behandlingsdager gir valideringsfeil`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig = null,
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = 5,
                    reisetilskudd = true,
                ),
            )

        assertEquals(true, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `Reisetilskudd og aktivitetIkkeMulig gir ikke valideringsfeil hvis perioder ikke overlapper`() {
        val perioder =
            listOf(
                Periode(
                    fom = LocalDate.of(2020, 9, 1),
                    tom = LocalDate.of(2020, 9, 10),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = false,
                ),
                Periode(
                    fom = LocalDate.of(2020, 9, 11),
                    tom = LocalDate.of(2020, 9, 20),
                    aktivitetIkkeMulig = null,
                    gradert = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    reisetilskudd = true,
                ),
            )

        assertEquals(false, harUlovligKombinasjonMedReisetilskudd(perioder))
    }

    @Test
    fun `behandletDato frem i tid gir valideringsfeil`() {
        val behandletDato = LocalDate.now().plusDays(1)

        assertEquals(true, erFremtidigDato(behandletDato))
    }

    @Test
    fun `behandletDato idag gir ikke valideringsfeil`() {
        val behandletDato = LocalDate.now()

        assertEquals(false, erFremtidigDato(behandletDato))
    }

    @Test
    fun `studentLisensSkalKasteFeil`() {
        val smRegistreringManuell =
            getSmRegistreringManuell(
                "12345678912",
                "12345678912",
                false,
            )
        val sykmelder =
            Sykmelder(
                "hpr",
                "12345678912",
                null,
                null,
                null,
                null,
                listOf(
                    Godkjenning(helsepersonellkategori = null, autorisasjon = Kode(true, 7704, "3"))
                ),
            )
        val validationResult =
            ValidationResult(
                Status.MANUAL_PROCESSING,
                ruleHits =
                    listOf(
                        RuleInfo(
                            ruleName = RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name,
                            messageForUser = "",
                            messageForSender = "",
                            ruleStatus = Status.MANUAL_PROCESSING,
                        ),
                    ),
            )

        val exception =
            assertFailsWith<ValidationException> {
                checkValidState(
                    smRegistreringManuell,
                    sykmelder,
                    validationResult = validationResult
                )
            }
        assertEquals(1, exception.validationResult.ruleHits.size)
        assertEquals(
            ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits =
                    listOf(
                        RuleInfo(
                            ruleName = RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name,
                            messageForSender =
                                "Studenter har ikke lov til å skrive sykmelding. Sykmelding må avvises.",
                            messageForUser = "Studenter har ikke lov til å skrive sykmelding.",
                            ruleStatus = Status.MANUAL_PROCESSING,
                        ),
                    ),
            ),
            exception.validationResult,
        )
    }
}
