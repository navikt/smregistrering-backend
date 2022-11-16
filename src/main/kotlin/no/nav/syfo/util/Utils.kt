package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.sikkerlogg

fun getAccessTokenFromAuthHeader(request: ApplicationRequest): String? {
    val authHeader = request.parseAuthorizationHeader()
    var accessToken: String? = null
    if (!(
        authHeader == null ||
            authHeader !is HttpAuthHeader.Single ||
            authHeader.authScheme != "Bearer"
        )
    ) {
        accessToken = authHeader.blob
    }
    return accessToken
}

fun padHpr(hprnummer: String?): String? {
    if (hprnummer?.length != null && hprnummer.length < 9) {
        return hprnummer.padStart(9, '0')
    }
    return hprnummer
}

fun changeHelsepersonellkategoriVerdiFromFAToFA1(godkjenninger: List<Godkjenning>): List<Godkjenning> {

    return if (godkjenninger.isNotEmpty()) {

        return godkjenninger.map {
            if (it.helsepersonellkategori?.verdi == "FA") {
                Godkjenning(
                    helsepersonellkategori = Kode(
                        aktiv = it.helsepersonellkategori.aktiv,
                        oid = it.helsepersonellkategori.oid,
                        verdi = "FA1"
                    ),
                    autorisasjon = it.autorisasjon
                )
            } else {
                Godkjenning(helsepersonellkategori = it.helsepersonellkategori, autorisasjon = it.autorisasjon)
            }
        }
    } else {
        godkjenninger
    }
}

fun logNAVEpostFromTokenToSecureLogsNoAccess(token: String) {
    try {
        val decodedJWT = JWT.decode(token)
        val navEpost = decodedJWT.claims["preferred_username"]?.asString()
        sikkerlogg.info("Logger ut navIdent: {}, har ikke tilgang", navEpost)
    } catch (exception: Exception) {
        sikkerlogg.info("Fikk ikkje hentet ut navIdent", exception)
    }
}
