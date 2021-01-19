package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.AuthenticationPipeline
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.http.HttpHeaders
import io.ktor.request.uri
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

val ignoreList = listOf("/is_ready", "/is_alive", "/prometheus")

fun Application.setupAuth(
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(Authentication) {
        addPhase(AuthenticationPipeline.CheckAuthentication)
        intercept(AuthenticationPipeline.CheckAuthentication) {
            if(!ignoreList.contains(context.request.uri)) {
                val r = this.context.authentication.principal
                val header = this.context.request.headers[HttpHeaders.Authorization]
                if (r == null && header != null) {
                    log.warn("Has ${HttpHeaders.Authorization} header, but it is empty or invalid for url: ${context.request.uri}")
                } else if (header == null) {
                    log.warn("Has no Authorization header for url: ${context.request.uri}")
                }
            }
        }
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
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
    return credentials.payload.audience.contains(vaultSecrets.smregistreringBackendClientId)
}
