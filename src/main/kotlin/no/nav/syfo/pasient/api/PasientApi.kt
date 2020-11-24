package no.nav.syfo.pasient.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import java.util.UUID
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.pasientApi(
    pdlPersonService: PdlPersonService
) {
    route("/api/v1") {
        get("/pasient/{pasientFnr}") {
            val pasientFnr = call.parameters["pasientFnr"]

            log.info("Mottok kall til GET /api/v1/pasient/$pasientFnr")

            when {
                pasientFnr == null -> {
                    log.info("Ugyldig path parameter: pasientFnr")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val accessToken = getAccessTokenFromAuthHeader(call.request)!!
                    val callId = UUID.randomUUID().toString()
                    val pdlPerson = pdlPersonService.getPdlPerson(fnr = pasientFnr, userToken = accessToken, callId = callId)
                    call.respond(pdlPerson.navn)
                }
            }
        }
    }
}