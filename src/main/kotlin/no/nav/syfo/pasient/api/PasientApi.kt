package no.nav.syfo.pasient.api

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.sikkerlogg
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.pasientApi(
    pdlPersonService: PdlPersonService,
    authorizationService: AuthorizationService,
) {
    route("/api/v1") {
        get("/pasient") {
            when (val pasientFnr = call.request.headers["X-Pasient-Fnr"]) {
                null -> {
                    log.info("Ugyldig header: pasientFnr is missing")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val accessToken = getAccessTokenFromAuthHeader(call.request)!!
                    val callId = UUID.randomUUID().toString()
                    if (authorizationService.hasAccess(accessToken, pasientFnr)) {
                        val pdlPerson = pdlPersonService.getPdlPerson(fnr = pasientFnr, callId = callId)
                        auditlogg.info(
                            AuditLogger().createcCefMessage(
                                fnr = pasientFnr,
                                accessToken = accessToken,
                                operation = AuditLogger.Operation.READ,
                                requestPath = "/api/v1/pasient",
                                permit = AuditLogger.Permit.PERMIT,
                            ),
                        )
                        call.respond(pdlPerson.navn)
                    } else {
                        log.warn("Veileder har ikke tilgang til pasient, $callId")

                        sikkerlogg.info(
                            "Veileder har ikkje tilgang navEmail:" +
                                "${JWT.decode(accessToken).claims["preferred_username"]!!.asString()}, {}",
                            StructuredArguments.keyValue("callId", callId),
                        )

                        auditlogg.info(
                            AuditLogger().createcCefMessage(
                                fnr = pasientFnr,
                                accessToken = accessToken,
                                operation = AuditLogger.Operation.READ,
                                requestPath = "/api/v1/pasient",
                                permit = AuditLogger.Permit.DENY,
                            ),
                        )

                        call.respond(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til pasienten")
                    }
                }
            }
        }
    }
}
