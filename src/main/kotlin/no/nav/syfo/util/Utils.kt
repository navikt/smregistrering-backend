package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.sikkerlogg
import java.io.IOException
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Throws(IOException::class, URISyntaxException::class)

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)

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

fun logNAVIdentTokenToSecureLogsWhenNoAccess(token: String) {
    try {
        val decodedJWT = JWT.decode(token)
        val navIdent = decodedJWT.claims["NAVident"]?.asString()
        sikkerlogg.info("Logger ut navIdent: {}, har ikke tilgang", navIdent)
    } catch (exception: Exception) {
        sikkerlogg.info("Fikk ikkje hentet ut navIdent", exception)
    }
}
