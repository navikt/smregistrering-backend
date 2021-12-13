package no.nav.syfo.pasient.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.registerPasientApi(
    pdlPersonService: PdlPersonService,
    authorizationService: AuthorizationService
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
                        call.respond(pdlPerson.navn)
                    } else {
                        log.warn("Veileder har ikke tilgang til pasient, $callId")
                        call.respond(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til pasienten")
                    }
                }
            }
        }
    }
}
