package no.nav.syfo.util

import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class UtilsTest {

    @Test
    internal fun `Change helsepersonellkategori verdi From FA to FA1`() {

        val godkjenninger = listOf(
            Godkjenning(
                helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "LE"),
                autorisasjon = Kode(true, 7704, "1")
            ),
            Godkjenning(
                helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "FA"),
                autorisasjon = Kode(true, 7704, "1")
            )
        )

        val changedGodkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(godkjenninger)

        godkjenninger.zip(changedGodkjenninger).all { (x, y) -> x == y } shouldBeEqualTo false
        changedGodkjenninger.firstOrNull { it.helsepersonellkategori?.verdi == "FA" }?.helsepersonellkategori?.verdi shouldBeEqualTo null
        changedGodkjenninger.firstOrNull { it.helsepersonellkategori?.verdi == "FA1" }?.helsepersonellkategori?.verdi shouldBeEqualTo "FA1"
    }

    @Test
    internal fun `Do not change helsepersonellkategori verdi`() {

        val godkjenninger = listOf(
            Godkjenning(
                helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "LE"),
                autorisasjon = Kode(true, 7704, "1")
            ),
            Godkjenning(
                helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "FA1"),
                autorisasjon = Kode(true, 7704, "1")
            )
        )

        val changedGodkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(godkjenninger)

        godkjenninger.zip(changedGodkjenninger).all { (x, y) -> x == y } shouldBeEqualTo true
        changedGodkjenninger.firstOrNull { it.helsepersonellkategori?.verdi == "FA" }?.helsepersonellkategori?.verdi shouldBeEqualTo null
        changedGodkjenninger.firstOrNull { it.helsepersonellkategori?.verdi == "FA1" }?.helsepersonellkategori?.verdi shouldBeEqualTo "FA1"
    }
}
