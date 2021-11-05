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
import no.nav.syfo.Environment
import no.nav.syfo.log

val ignoreList = listOf("/is_ready", "/is_alive", "/prometheus")

fun Application.setupAuth(
    environment: Environment,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(Authentication) {
        addPhase(AuthenticationPipeline.CheckAuthentication)
        intercept(AuthenticationPipeline.CheckAuthentication) {
            if (!ignoreList.contains(context.request.uri)) {
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
                    hasSmregistreringBackendClientAudience(credentials, environment) -> JWTPrincipal(credentials.payload)
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

fun hasSmregistreringBackendClientAudience(credentials: JWTCredential, env: Environment): Boolean {
    return credentials.payload.audience.contains(env.azureAppClientId)
}
