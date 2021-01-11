package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

fun Application.setupAuth(
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(Authentication) {
        jwt(name = "jwt") {
            log.info("auth 1")
            verifier(jwkProvider, issuer)
            log.info("auth 2")
            validate { credentials ->
                when {
                    hasSyfosmmanuellBackendClientIdAudience(credentials, vaultSecrets) -> JWTPrincipal(credentials.payload)
                    else -> {
                        unauthorized(credentials)
                    }
                }
            }
        }
    }
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
            "Auth: Unexpected audience for jwt {}, {}",
            StructuredArguments.keyValue("issuer", credentials.payload.issuer),
            StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasSyfosmmanuellBackendClientIdAudience(credentials: JWTCredential, vaultSecrets: VaultSecrets): Boolean {
    log.info("auth 3")
    return credentials.payload.audience.contains(vaultSecrets.smregistreringBackendClientId)
}
